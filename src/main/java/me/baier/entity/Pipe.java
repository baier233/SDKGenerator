package me.baier.entity;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;
import me.baier.ClassesResolver;

import java.io.IOException;
import java.util.zip.ZipOutputStream;

public interface Pipe {

    void runObfuscate(ClassesResolver blastObfuscate);

    byte[] writeClassBytes(ClassesResolver blastObfuscate, ClassWriter classWriter);

    void init(ClassesResolver blastObfuscate, ClassNode classNode);

    void process(ClassesResolver blastObfuscate, ClassNode classNode) throws Exception;

    void finish(ClassesResolver blastObfuscate, ZipOutputStream outputStream) throws IOException;

}
