package me.baier;

import lombok.Getter;
import lombok.Setter;
import me.baier.config.Settings;
import me.baier.entity.Pipe;
import me.baier.util.ZipUtil;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;

import me.baier.entity.Tuple;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

@Getter
public class ClassesResolver {
	public static ClassesResolver INSTANCE;
	private File inputFile;

	private final File outputDir;

	private final List<Pipe> pipeLines = new ArrayList<>();

	private final List<String> filterClasses = new ArrayList<>();


	private final HashMap<String,Tuple<ClassNode, ClassReader>> classNodes = new HashMap<>();

	private final Map<ClassNode, byte[]> classNodeOpcodesMap = new HashMap<>();

	private final Settings settings;

	@Setter
	private int writerFlag = ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES;
	ClassesResolver(Settings settings){
		INSTANCE = this;
		this.settings = settings;
		this.outputDir = settings.getOUT_PUT_DIR();
	}
	public ClassesResolver addPipeLine(Pipe line) {
		pipeLines.add(line);
		return this;
	}

	public ClassesResolver addFilterClass(String className) {
		filterClasses.add(className);
		return this;
	}

	public ClassesResolver setInputFile(File inputFile) {
		this.inputFile = inputFile;
		return this;
	}

	public ClassNode getNode(String className){
		Tuple<ClassNode, ClassReader> classNode = classNodes.get(className);
		if (classNode == null) return null;
		return classNode.getFirst();
	}

	public void resolve() throws IOException {

		try (ZipFile jarFile = new ZipFile(inputFile)) {
			Enumeration<? extends ZipEntry> entries = jarFile.entries();
			while (entries.hasMoreElements()) {
				ZipEntry entry = entries.nextElement();
				if (!entry.getName().endsWith(".class")) continue;
				try (InputStream inputStream = jarFile.getInputStream(entry)) {
					byte[] opcodes = ZipUtil.readStream(inputStream);
					ClassReader classReader = new ClassReader(opcodes);
					ClassNode classNode = new ClassNode();
					classNodeOpcodesMap.put(classNode, opcodes);
					classReader.accept(classNode, 0);
					classNodes.put(classNode.name ,new Tuple<>(classNode, classReader));
					for (Pipe pipeLine : pipeLines) {
						pipeLine.init(this, classNode);
					}
				}
			}
			for (Tuple<ClassNode, ClassReader> tuple : classNodes.values()) {
				writerFlag = ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES;
				for (Pipe pipeLine : pipeLines) {
					Objects.requireNonNull(pipeLine).process(this, tuple.getFirst());
				}
			}
		} catch (Exception e) {
			throw new RuntimeException(e);
		}

	}
}
