package sm
package imm
package opcodes

import sm.imm.Type
import sm.{virt, VmThread}
import sm.virt.Obj

object Misc {
  case class Goto(label: Int) extends BaseOpCode(167, "goto"){
    def op = vt => vt.frame.pc = label
  }

  // These guys are meant to be deprecated in java 6 and 7
  //===============================================================
  val Ret = UnusedOpCode(169, "ret")
  val Jsr = UnusedOpCode(168, "jsr")
  //===============================================================

  case class TableSwitch(min: Int, max: Int, defaultTarget: Int, targets: Seq[Int]) extends BaseOpCode(170, "tableswitch"){
    def op = vt => {
      val virt.Int(top) = vt.pop
      val newPc: Int =
        if (targets.isDefinedAt(top - min)) targets(top - min)
        else defaultTarget
      vt.frame.pc = newPc
    }
  }
  case class LookupSwitch(defaultTarget: Int, keys: Seq[Int], targets: Seq[Int]) extends BaseOpCode(171, "lookupswitch"){
    def op = vt => {
      val virt.Int(top) = vt.pop
      val newPc: Int = keys.zip(targets).toMap.get(top).getOrElse(defaultTarget: Int)
      vt.frame.pc = newPc
    }
  }

  case object IReturn extends BaseOpCode(172, "ireturn"){ def op = vt => vt.returnVal(Some(vt.frame.stack.head)) }
  case object LReturn extends BaseOpCode(173, "lreturn"){ def op = vt => vt.returnVal(Some(vt.frame.stack.head)) }
  case object FReturn extends BaseOpCode(174, "freturn"){ def op = vt => vt.returnVal(Some(vt.frame.stack.head)) }
  case object DReturn extends BaseOpCode(175, "dreturn"){ def op = vt => vt.returnVal(Some(vt.frame.stack.head)) }
  case object AReturn extends BaseOpCode(176, "areturn"){ def op = vt => vt.returnVal(Some(vt.frame.stack.head)) }
  case object Return extends BaseOpCode(177, "return"){ def op = vt => vt.returnVal(None) }

  case class GetStatic(owner: Type.Cls, name: String, desc: Type) extends BaseOpCode(178, "getstatic"){
    def op = vt => {
      import vt.vm
      import vm._
      vt.push(owner.cls.apply(owner, name).toStackVal)
    }
  }
  case class PutStatic(owner: Type.Cls, name: String, desc: Type) extends BaseOpCode(179, "putstatic"){
    def op = vt => {
      import vt.vm
      import vm._
      owner.cls.update(owner, name, vt.pop)
    }
  }

  case class GetField(owner: Type.Cls, name: String, desc: Type) extends BaseOpCode(180, "getfield"){
    def op = vt => vt.pop match {
      case (objectRef: virt.Obj) => vt.push(objectRef(owner, name).toStackVal)
      case virt.Null =>
        import vt._
        vt.throwException(virt.Obj("java/lang/NullPointerException"))
    }
  }
  case class PutField(owner: Type.Cls, name: String, desc: Type) extends BaseOpCode(181, "putfield"){
    def op = vt => (vt.pop, vt.pop) match {
      case (value, objectRef: virt.Obj) =>
        objectRef(owner, name) = value
      case (value, virt.Null) =>
        import vt._
        vt.throwException(virt.Obj("java/lang/NullPointerException"))
    }
  }

  def ensureNonNull(vt: VmThread, x: Any)(thunk: => Unit) = {
    import vt._
    if (x == virt.Null){
      throwException(virt.Obj("java/lang/NullPointerException"))
    }else {
      thunk
    }
  }

  case class InvokeVirtual(owner: Type.Entity, name: String, desc: Type.Desc) extends BaseOpCode(182, "invokevirtual"){
    def op = vt => {
      import vt.vm
      val argCount = desc.args.length
      val (args, rest) = vt.frame.stack.splitAt(argCount+1)
      ensureNonNull(vt, args.last){
        val objType =
          args.last match{
            case a: virt.Obj => a.cls.clsData.tpe
            case _ => owner
          }



        vt.frame.stack = rest
        vt.prepInvoke(objType, name, desc, args.reverse)
      }
    }
  }
  case class InvokeSpecial(owner: Type.Cls, name: String, desc: Type.Desc) extends BaseOpCode(183, "invokespecial"){
    def op = implicit vt => {
      val argCount = desc.args.length
      val (args, rest) = vt.frame.stack.splitAt(argCount+1)
      vt.frame.stack = rest
      vt.prepInvoke(owner, name, desc, args.reverse)
    }
  }
  case class InvokeStatic(owner: Type.Cls, name: String, desc: Type.Desc) extends BaseOpCode(184, "invokestatic"){

    def op = vt => {
      val argCount = desc.args.length
      val (args, rest) = vt.frame.stack.splitAt(argCount)
      vt.frame.stack = rest
      vt.prepInvoke(owner, name, desc, args.reverse)
    }
  }
  case class InvokeInterface(owner: Type.Cls, name: String, desc: Type.Desc) extends BaseOpCode(185, "invokeinterface"){
    def op = InvokeVirtual(owner, name, desc).op
  }

  case class InvokeDynamic(name: String, desc: String, bsm: Object, args: Object) extends BaseOpCode(186, "invokedynamic"){ def op = ??? }

  case class New(desc: Type.Cls) extends BaseOpCode(187, "new"){
    def op = implicit vt => desc match {
      case _ =>
        import vt.vm._
        vt.push(new virt.Obj(desc)(vt.vm))
    }
  }
  case class NewArray(typeCode: Int) extends BaseOpCode(188, "newarray"){
    def op = vt => {
      val virt.Int(count) = vt.pop

      val newArray = typeCode match{
        case 4  => virt.PrimArr[Boolean](count)
        case 5  => virt.PrimArr[Char](count)
        case 6  => virt.PrimArr[Float](count)
        case 7  => virt.PrimArr[Double](count)
        case 8  => virt.PrimArr[Boolean](count)
        case 9  => virt.PrimArr[Short](count)
        case 10 => virt.PrimArr[Int](count)
        case 11 => virt.PrimArr[Long](count)
      }
      vt.push(newArray)
    }
  }
  case class ANewArray(desc: Type.Entity) extends BaseOpCode(189, "anewarray"){
    def op = vt => {
      val virt.Int(count) = vt.pop
      vt.push(virt.ObjArr(desc, count))
    }
  }

  case object ArrayLength extends BaseOpCode(190, "arraylength"){
    def op = vt => {
      vt.push(vt.pop.asInstanceOf[virt.Arr].backing.length)
    }
  }

  case object AThrow extends BaseOpCode(191, "athrow"){
    def op = vt => {

      vt.throwException(vt.pop.asInstanceOf[virt.Obj])

    }
  }
  case class CheckCast(desc: Type) extends BaseOpCode(192, "checkcast"){
    def op = vt => {

    }
  }

  case class InstanceOf(desc: Type) extends BaseOpCode(193, "instanceof"){
    def op = implicit vt => {

      import vt._
      import vm._
      val res = vt.pop match{
        case virt.Null => 0
        case x: virt.Obj =>

          if(x.cls.checkIsInstanceOf(desc)) 1 else 0
        case x: virt.Arr => desc match {
          case imm.Type.Cls("java/lang/Object") => 1
          case imm.Type.Arr(innerType) => 1
          case _ => 0
        }
        case _ => 0
      }

      vt.push(res)
    }
  }
  case object MonitorEnter extends BaseOpCode(194, "monitorenter"){
    def op = _.pop
  }
  case object MonitorExit extends BaseOpCode(195, "monitorexit"){
    def op = _.pop
  }

  // Not used, because ASM folds these into the following bytecode for us
  //===============================================================
  val Wide = UnusedOpCode(196, "wide")
  //===============================================================

  case class MultiANewArray(desc: Type.Arr, dims: Int) extends BaseOpCode(197, "multianewarray"){
    def op = vt => {
      def rec(dims: List[Int], tpe: Type.Entity): virt.Arr = {

        (dims, tpe) match {
          case (size :: Nil, Type.Arr(innerType @ Type.Prim(c))) =>
            c match {
              case 'Z' => virt.PrimArr[Boolean](size)
              case 'B' => virt.PrimArr[Byte](size)
              case 'C' => virt.PrimArr[Char](size)
              case 'S' => virt.PrimArr[Short](size)
              case 'I' => virt.PrimArr[Int](size)
              case 'F' => virt.PrimArr[Float](size)
              case 'J' => virt.PrimArr[Long](size)
              case 'D' => virt.PrimArr[Double](size)
            }
          case (size :: Nil, Type.Arr(innerType)) =>
            new virt.ObjArr(innerType, Array.fill[virt.Val](size)(Type.CharClass.default(innerType)))
          case (size :: tail, Type.Arr(innerType)) =>
            new virt.ObjArr(innerType, Array.fill[virt.Val](size)(rec(tail, innerType)))
        }
      }
      val (dimValues, newStack) = vt.frame.stack.splitAt(dims)
      val dimArray = dimValues.map(x => x.asInstanceOf[virt.Int].v).toList
      val array = rec(dimArray, desc)
      vt.push(array)
    }
  }

  case class IfNull(label: Int) extends BaseOpCode(198, "ifnull"){
    def op = vt => {
      if (vt.pop == virt.Null) vt.frame.pc = label
    }
  }

  case class IfNonNull(label: Int) extends BaseOpCode(199, "ifnonnull"){
    def op = vt => {
      if (vt.pop != virt.Null) vt.frame.pc = label
    }
  }

  // Not used, because ASM converts these to normal Goto()s and Jsr()s
  //===============================================================
  val GotoW = UnusedOpCode(200, "goto_w")
  val JsrW = UnusedOpCode(201, "jsr_w")
  //===============================================================

}
