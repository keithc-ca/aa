package com.cliffc.aa.type;

import com.cliffc.aa.AA;

import java.util.BitSet;
import java.util.HashMap;
import java.util.function.Consumer;
import java.util.function.Predicate;

// Named types are essentially a subclass of the named type.
// They also must be used to make recursive types.  Examples:
//   A.B.int << B.int << int   // Subtypes
//     B.int.isa(int)
//   A.B.int.isa(B.int)
//     C.int.meet(B.int) == int
//   A.B.int.meet(C.int) == int
//
//   A.B.~int.join(B.~int) == A.B.~int
//     C.~int.join(B.~int) ==     ~int
//
//   B. int.meet(  ~int) == B. int.meet(B.~int) == B.int
//   B.~int.meet(   int) ==    int
//   B.~int.meet(C. int) ==    int
//   B.~int.meet(B. int) == B. int
//   B.~int.meet(C.~int) ==    int // Nothing in common, fall to int
//   B.~int.meet(  ~int) == B.~int
// A.B.~int.meet(B.~int) == A.B.~int // both high, keep long; short guy high, keep long
// A.B.~int.meet(B. int) ==   B. int // one low, keep low   ; short guy  low, keep short
// A.B. int.meet(B.~int) == A.B. int // one low, keep low   ; short guy high, keep long
// A.B. int.meet(B. int) ==   B. int // both low, keep short; short guy  low, keep short
//
// A.B.~int.meet(D.B.~int) == B.int // Nothing in common, fall to int

public class TypeName extends TypeObj<TypeName> {
  public  String _name;
  public  HashMap<String,Type> _lex; // Lexical scope of this named type
  public  Type _t;                // Named type
  public  short _depth; // Nested depth of TypeNames, or -1/ for a forward-ref type-var, -2 for type-cycle head
  // Named type variable
  private TypeName ( String name, HashMap<String,Type> lex, Type t, short depth ) { super(TNAME,false); init(name,lex,t,depth); }
  private void init( String name, HashMap<String,Type> lex, Type t, short depth ) { super.init(TNAME,false); assert name!=null && lex !=null; _name=name; _lex=lex; _t=t; _depth = depth; }
  private static short depth( Type t ) { return(short)(t instanceof TypeName ? ((TypeName)t)._depth+1 : 0); }
  // Hash does not depend on other types.
  // No recursion on _t to break type cycles
  @Override int compute_hash() { return super.compute_hash() + _name.hashCode(); }
  @Override public boolean equals( Object o ) {
    if( this==o ) return true;
    if( !(o instanceof TypeName) ) return false;
    TypeName t2 = (TypeName)o;
    if( _lex != t2._lex  || !_name.equals(t2._name) || _t!=t2._t ) return false;
    if( _depth==t2._depth ) return true;
    // Also return true for comparing TypeName(name,type) where the types
    // match, but the 'this' TypeName is depth 0 vs depth -1 - this detects
    // simple cycles and lets the interning close the loop.
    return (t2._depth<0 ? 0 : t2._depth) == (_depth<0 ? 0 :_depth);
  }
  @Override public boolean cycle_equals( Type o ) {
    if( this==o ) return true;
    if( !(o instanceof TypeName) ) return false;
    TypeName t2 = (TypeName)o;
    if( _lex != t2._lex  || !_name.equals(t2._name) ) return false;
    if( _t!=t2._t && !_t.cycle_equals(t2._t) ) return false;
    if( _depth==t2._depth ) return true;
    // Also return true for comparing TypeName(name,type) where the types
    // match, but the 'this' TypeName is depth 0 vs depth -1 - this detects
    // simple cycles and lets the interning close the loop.
    return (t2._depth<0 ? 0 : t2._depth) == (_depth<0 ? 0 :_depth);
  }
  @Override String str( BitSet dups) {
    if( _depth < 0 ) {          // Only for recursive-type-heads
      if( dups == null ) dups = new BitSet();
      else if( dups.get(_uid) ) return _name; // Break recursive cycle
      dups.set(_uid);
    }
    return _name+":"+_t.str(dups);
  }

  private static TypeName FREE=null;
  @Override protected TypeName free( TypeName ret ) { FREE=this; return ret; }
  private static TypeName make0( String name, HashMap<String,Type> lex, Type t, short depth) {
    TypeName t1 = FREE;
    if( t1 == null ) t1 = new TypeName(name,lex,t,depth);
    else { FREE = null; t1.init(name,lex,t,depth); }
    TypeName t2 = (TypeName)t1.hashcons();
    // Close some recursions: keep -2 and -1 depths vs 0
    if( t1._depth < t2._depth )
      t2._depth = t1._depth;    // keep deeper depth
    return t1==t2 ? t1 : t1.free(t2);
  }

  public static TypeName make( String name, HashMap<String,Type> lex, Type t) {
    TypeName tn0 = make0(name,lex,t,depth(t));
    TypeName tn1 = (TypeName)lex.get(name);
    if( tn1==null || tn1._depth!= -2 || RECURSIVE_MEET>0 ) return tn0;
    return tn0.make_recur(tn1,0,new BitSet());
  }
  public TypeName make( Type t) { return make(_name,_lex,t); }
  public static TypeName make_forward_def_type( String name, HashMap<String,Type> lex ) { return make0(name,lex,TypeStruct.ALLSTRUCT,(short)-1); }

          static final HashMap<String,Type> TEST_SCOPE = new HashMap<>();
          static final TypeName TEST_ENUM = make("__test_enum",TEST_SCOPE,TypeInt.INT8);
          static final TypeName TEST_FLT  = make("__test_flt" ,TEST_SCOPE,TypeFlt.FLT32);
          static final TypeName TEST_E2   = make("__test_e2"  ,TEST_SCOPE,TEST_ENUM);
  public  static final TypeName TEST_STRUCT=make("__test_struct",TEST_SCOPE,TypeStruct.POINT);

  static final TypeName[] TYPES = new TypeName[]{TEST_ENUM,TEST_FLT,TEST_E2,TEST_STRUCT};

  @Override protected TypeName xdual() { return new TypeName(_name,_lex,_t. dual(),_depth); }
  @Override TypeName rdual() {
    if( _dual != null ) return _dual;
    TypeName dual = _dual = new TypeName(_name,_lex,_t.rdual(),_depth);
    dual._dual = this;
    dual._hash = compute_hash();
    dual._cyclic = true;
    return dual;
  }
  @Override protected Type xmeet( Type t ) {
    switch( t._type ) {
    case TNIL:
      // Cannot swap args and go again, because it screws up the cyclic_meet.
      // This means we handle name-meet-nil right here.
      return meet_nil();

    case TNAME:
      // Matching inner names can be kept.  If one side is an extension of the
      // other, we keep the low-side prefix (long or short).  If there is no
      // match on a name, then the result must fall- even if both inner types
      // and names are the same (but high).
      TypeName tn = (TypeName)t;
      int thisd =    _depth<0 ? 0 :   _depth;
      int thatd = tn._depth<0 ? 0 : tn._depth;
      // Recursive on depth until depths are equal
      if( thisd > thatd ) return extend(t);
      if( thatd > thisd ) return tn.extend(this);
      Type mt = _t.meet(tn._t);
      if( _name.equals(tn._name) )   // Equal names?
        return make(_name,_lex,mt); // Peel name and meet
      // Unequal names
      if( !mt.above_center() ) return mt;
      // Unequal high names... fall to the highest value below-center
      return off_center(mt);

    default:
      return extend(t);
    }
  }

  // Longer side is 'this'.  Shorter side is 'tn'.
  private Type extend(Type t) {
    Type x = _t.meet(t); // Recursive, removing longer name
    int xnd = x instanceof TypeName ? ((TypeName)x)._depth : -1;
    int tnd = t instanceof TypeName ? ((TypeName)t)._depth : -1;
    if( xnd < tnd ) return x; // No common prefix
    if( x==Type.ALL || x==Type.NSCALR || x==Type.SCALAR || x==TypeObj.OBJ ) return x;
    // Same strategy as TypeStruct and extra fields...
    // short guy high, keep long
    // short guy  low, keep short
    if( t.above_center() ) return make(_name,_lex,x);
    // Fails lattice if 'x' is a constant because cannot add names...
    // Force 'x' to not be a constant.
    return off_center(x);
  }

  // Must fall to the 1st thing just below center
  private static Type off_center(Type mt) {
    if( !mt.above_center() && !mt.is_con() ) return mt; // Already below-center
    switch( mt._type ) {
    case TXNUM: case TXNNUM: case TXREAL: case TXNREAL:
    case TINT:  case TFLT:
      // Return a number that is not-null (to preserve any not-null-number
      // property) but forces a move off the centerline.
      return mt.must_nil() ? TypeInt.BOOL : TypeInt.make(-1,1,1);
    case TNAME:
      return mt;
    default: throw AA.unimpl();
    }
  }

  // 'this' is a forward ref type definition; the actual type-def is 't' which
  // may include embedded references to 'this'
  @Override public TypeName merge_recursive_type( Type t ) {
    if( _depth >= 0 ) return null; // Not a recursive type-def
    assert _t==TypeStruct.ALLSTRUCT;
    // Remove from INTERN table, since hacking type will not match hash
    untern()._dual.untern();
    // Hack type and it's dual.  Type is now recursive.
    _t = t;
    _dual._t = t._dual;
    _depth = _dual._depth = -2;
    // Flag all as cyclic
    t.mark_cycle(this,new BitSet(),new BitSet());
    // Back into the INTERN table
    retern()._dual.retern();

    return this;
  }

  @Override public boolean above_center() { return _t.above_center(); }
  @Override public boolean may_be_con() { return _depth >= 0 && _t.may_be_con(); }
  @Override public boolean is_con()   { return _depth >= 0 && _t.is_con(); } // No recursive structure is a constant
  @Override public double getd  () { return _t.getd  (); }
  @Override public long   getl  () { return _t.getl  (); }
  @Override public String getstr() { return _t.getstr(); }
  @Override public boolean must_nil() { return _t.must_nil(); }
  @Override Type not_nil() {
    Type nn = _t.not_nil();
    //if( !_t.above_center() ) return nn;
    return make(_name,_lex,nn);
  }
  // Since meeting an unnamed NIL, end result is never high and never named
  @Override public Type meet_nil() { return _t.meet_nil(); }
  @Override public TypeObj startype() { return make(_name,_lex,_t.startype()); }
  @Override public byte isBitShape(Type t) {
    if( t instanceof TypeName ) {
      if( ((TypeName)t)._name.equals(_name) ) return _t.isBitShape(((TypeName)t)._t);
      return 99; // Incompatible names do not mix
    }
    return _t.isBitShape(t); // Strip name and try again
  }
  @Override TypeName make_recur(TypeName tn, int d, BitSet bs ) {
    if( bs.get(_uid) ) return this; // Looping on some other recursive type
    bs.set(_uid);
    // Make a (possibly cyclic & infinite) named type.  Prevent the infinite
    // unrolling of names by not allowing a named-type with depth >= D from
    // holding (recursively) the head of a named-type cycle.  We need to cap the
    // unroll, to prevent loops/recursion from infinitely unrolling.
    int D = 5;
    if( _lex==tn._lex && _name.equals(tn._name) && d++ == D )
      return above_center() ? tn.dual() : tn;
    Type t2 = _t.make_recur(tn,d,bs);
    return t2==_t ? this : make0(_name,_lex,t2,_depth);
  }
  // Mark if part of a cycle
  @Override void mark_cycle( Type head, BitSet visit, BitSet cycle ) {
    if( visit.get(_uid) ) return;
    visit.set(_uid);
    if( this==head ) { cycle.set(_uid); _cyclic=_dual._cyclic=true; }
    _t.mark_cycle(head,visit,cycle);
    if( cycle.get(_t._uid) )
      { cycle.set(_uid); _cyclic=_dual._cyclic=true; }
  }

  // Iterate over any nested child types
  @Override public void iter( Consumer<Type> c ) { c.accept(_t); }
  @Override boolean contains( Type t, BitSet bs ) { return _t == t || _t.contains(t, bs); }
  @Override int depth( BitSet bs ) { return 1+_t.depth(bs); }
  @SuppressWarnings("unchecked")
  @Override Type replace( Type old, Type nnn, HashMap<Type,Type> HASHCONS ) {
    Type x = _t.replace(old,nnn,HASHCONS);
    if( x==_t ) return this;
    Type rez = make(_name,_lex,x);
    rez._cyclic=true;
    TypeName hc = (TypeName)HASHCONS.get(rez);
    if( hc == null ) { HASHCONS.put(rez,rez); return rez; }
    return rez.free(hc);
  }

  @SuppressWarnings("unchecked")
  @Override void walk( Predicate<Type> p ) { if( p.test(this) ) _t.walk(p); }
  @Override TypeStruct repeats_in_cycles(TypeStruct head, BitSet bs) { return _cyclic ? _t.repeats_in_cycles(head,bs) : null; }
}
