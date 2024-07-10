package me.baier;

import me.baier.components.pipes.SDKGeneratorPipe;
import me.baier.config.Settings;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public class Main {
	public static void main(String[] args) throws IOException {
		if (args.length < 2) {
			System.out.println("[me.baier.Main]Usage: java -jar <jar file> <input file> <output dir> [[optional]]<path to filter>");
			return;
		}
		File inputFile = new File(args[0]);
		if (!Files.exists(inputFile.toPath())) {
			System.out.println("[me.baier.Main]Input file does not exist");
			return;
		}

		File outputDir = new File(args[1]);
		if (!outputDir.exists()) {
			boolean ignore = outputDir.mkdirs();
		}
		String path2Filter = null;
		if (args.length > 2){
			path2Filter = args[2];
		}

		new ClassesResolver(new Settings(outputDir,path2Filter))
				.setInputFile(inputFile)
				.addPipeLine(new SDKGeneratorPipe("SDKGeneratorPipe"))
				.resolve();

		System.out.println("[me.baier.Main]Finished!");
	}
}
