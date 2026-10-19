import java.nio.file.*;
import org.objectweb.asm.*;

/** Build-time only; reject unexpected Xerr bytecode instead of partially patching it. */
public final class PatchXerr {
    public static void main(String[] args) throws Exception {
        var reader=new ClassReader(Files.readAllBytes(Path.of(args[0])));
        var writer=new ClassWriter(reader,ClassWriter.COMPUTE_MAXS);
        int[] count={0};
        reader.accept(new ClassVisitor(Opcodes.ASM9,writer) {
            @Override public MethodVisitor visitMethod(int access,String name,String desc,String signature,String[] exceptions) {
                var delegate=super.visitMethod(access,name,desc,signature,exceptions);
                if(!name.equals("buildComponentStringInternal")) return delegate;
                return new MethodVisitor(Opcodes.ASM9,delegate) {
                    @Override public void visitMethodInsn(int opcode,String owner,String method,String descriptor,boolean itf) {
                        super.visitMethodInsn(opcode,owner,method,descriptor,itf);
                        if(owner.equals("com/hiusers/xerr/taboolib/platform/compat/PlaceholderExpansionKt") &&
                           method.equals("replacePlaceholder") && descriptor.equals("(Ljava/lang/String;Lorg/bukkit/entity/Player;)Ljava/lang/String;")) {
                            super.visitMethodInsn(Opcodes.INVOKESTATIC,"com/hiusers/xerr/compat/LayoutMiniMessage","prepare","(Ljava/lang/String;)Ljava/lang/String;",false);
                            count[0]++;
                        }
                    }
                };
            }
        },0);
        if(count[0]!=3) throw new IllegalStateException("Unsupported Xerr layout bytecode: matched "+count[0]+" instead of 3");
        Files.write(Path.of(args[1]),writer.toByteArray());
    }
}
