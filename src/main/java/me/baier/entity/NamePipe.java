package me.baier.entity;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.objectweb.asm.ClassWriter;
import me.baier.ClassesResolver;

@Getter
@AllArgsConstructor
public abstract class NamePipe implements Pipe {

    private String preName;

    @Override
    public void runObfuscate(ClassesResolver blastObfuscate) {
    }

    @Override
    public byte[] writeClassBytes(ClassesResolver blastObfuscate, ClassWriter classWriter) {
        return classWriter.toByteArray();
    }
}
