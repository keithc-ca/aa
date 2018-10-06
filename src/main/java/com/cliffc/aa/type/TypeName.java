package com.cliffc.aa.type;

import com.cliffc.aa.AA;

import java.util.HashSet;

// Named types are essentially a subclass of the named type.
public class TypeName extends Type<TypeName> {
  public  String _name;
  public  Type _t;
  private short _depth;         // Nested depth of TypeNames, or -1 for a forward-ref type-var
  // Named type variable
  private TypeName ( String name, Type t, short depth ) { super(TNAME); init(name,t,depth); }
  private void init( String name, Type t, short depth ) { assert name!=null; _name=name; _t=t; _depth = depth; }
  private static short depth( Type t ) { return(short)(t instanceof TypeName ? ((TypeName)t)._depth+1 : 0); }
  @Override public int hashCode( ) { return TNAME+(_name==null?0:_name.hashCode())+_t.hashCode()+_depth;  }
  @Override public boolean equals( Object o ) {
    if( this==o ) return true;
    if( !(o instanceof TypeName) ) return false;
    TypeName t2 = (TypeName)o;
    return _t==t2._t && _depth==t2._depth && _name.equals(t2._name);
  }
  @Override String str(HashSet<Type> dups) {
    if( _depth == -1 ) {        // Only for recursive-type-heads
      if( dups == null ) dups = new HashSet<>();
      else if( dups.contains(this) ) return _name; // Break recursive cycle
      dups.add(this);
    }
    return _name+":"+_t.str(dups);
  }
  
  private static TypeName FREE=null;
  @Override protected TypeName free( TypeName f ) { FREE=f; return this; }
  private static TypeName make0( String name, Type t, short depth) {
    assert !(t instanceof TypeUnion) || t==TypeUnion.NIL; // No named unions (except nil)
    TypeName t1 = FREE;
    if( t1 == null ) t1 = new TypeName(name,t,depth);
    else { FREE = null; t1.init(name,t,depth); }
    TypeName t2 = (TypeName)t1.hashcons();
    return t1==t2 ? t1 : t2.free(t1);
  }
  public static TypeName make( String name, Type t) { return make0(name,t,depth(t)); }
  public static TypeName make_forward_def_type( String name ) { return make0(name,Type.SCALAR,(short)-1); }

  public  static final TypeName TEST_ENUM = make("__test_enum",TypeInt.INT8);
  private static final TypeName TEST_FLT  = make("__test_flt" ,TypeFlt.FLT32);
  private static final TypeName TEST_E2   = make("__test_e2"  ,TEST_ENUM);
  
  static final TypeName[] TYPES = new TypeName[]{TEST_ENUM,TEST_FLT,TEST_E2};

  @Override protected TypeName xdual() { return new TypeName(_name,_t.dual(),depth(_t)); }
  @Override protected Type xmeet( Type t ) {
    assert t != this;
    Type mt;
    switch( t._type ) {
    case TUNION: return t.xmeet(this); // Let other side decide
    case TNAME:
      TypeName tn = (TypeName)t;
      if( tn._depth > _depth ) return tn.xmeet(this); // Deeper on 'this'
      mt = _t.meet(tn._t);      // Peel name and meet
      if( tn._depth== _depth && _name.equals(tn._name) )
        return make(_name,mt);
      break;                           // Names or depth unequal; treat as unnamed
    default:
      // LHS is named, RHS is unnamed.  If the RHS is high, can keep the name.
      mt = _t.meet(t);
      if( t.above_center() && !Type.SCALAR.isa(mt) ) return make(_name,mt);
      if( !mt.is_con() ) return mt;
      break;
    }
    // Must fall to the least-upper-bound - which is below the centerline (all
    // constants).  i.e., must fall below any constant; mixing in a null
    // generally works.
    if( mt.may_be_nil() ) {     // mixing in a nil will not drop
      if( mt.isa(Type.NUM) ) return mt.meet(TypeInt.BOOL);
      throw AA.unimpl();
    }
    return mt.meet_nil();
  }

  // 'this' is a forward ref type definition; the actual type-def is 't' which
  // may include embedded references to 'this'
  @Override public TypeName merge_recursive_type( Type t ) {
    if( _depth != -1 ) return null; // Not a recursive type-def
    assert _t==Type.SCALAR;
    // Remove from INTERN table, since hacking type will not match hash
    untern();
    _dual.untern();
    // Hack type and it's dual.  Type is now recursive.
    _t = t;
    ((TypeName)_dual)._t = t._dual;
    // DO not install recursive-type back into the INTERN table.  The
    // hashCode() and equals() calls are not prepared to handle the recursive
    // structure and will stack-overflow instead of returning sensible results.
    // But the types are still pointer-unique and can be compared with normal
    // pointer-equality checks.
    return this;
  }
  
  @Override public boolean above_center() { return _t.above_center(); }
  @Override public boolean may_be_con() { return _depth != -1 && _t.may_be_con(); }
  @Override public boolean is_con()   { return _depth != -1 && _t.is_con(); } // No recursive structure is a constant
  @Override public boolean may_be_nil() { return _t.may_be_nil(); }
  @Override public boolean may_have_nil() { return _t.may_have_nil(); }
  @Override public double getd  () { return _t.getd  (); }
  @Override public long   getl  () { return _t.getl  (); }
  @Override public String getstr() { return _t.getstr(); }
  @Override public byte isBitShape(Type t) {
    if( t instanceof TypeName ) {
      if( ((TypeName)t)._name.equals(_name) ) return _t.isBitShape(((TypeName)t)._t);
      return 99; // Incompatible names do not mix
    }
    return _t.isBitShape(t);
  }
}
