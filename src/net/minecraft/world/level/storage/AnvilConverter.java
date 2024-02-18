package net.minecraft.world.level.storage;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import com.mojang.nbt.CompoundTag;
import com.mojang.nbt.NbtIo;

import net.minecraft.world.level.chunk.storage.IndevLevelStorage;
import net.minecraft.world.level.chunk.storage.RegionFile;

/**
 * Modded by hoshinetsu Copyright Mojang AB.
 * 
 * Don't do evil.
 */

public class AnvilConverter {

	public static void main(String[] args) throws IOException {
		long start = System.currentTimeMillis();
		if (args.length != 2) {
			System.err.println("None or too many arguments specified.");
			System.out.println("");
			printUsageAndExit();
			return;
		}

		File indevLevel;
		try {
			indevLevel = new File(args[0]);
			if (!indevLevel.exists()) {
				throw new RuntimeException(args[0] + " doesn't exist");
			} else if (!indevLevel.isFile()) {
				throw new RuntimeException(args[0] + " is not a file");
			}
		} catch (Exception e) {
			System.err.println("Base file problem: " + e.getMessage());
			System.out.println("");
			printUsageAndExit();
			return;
		}

		File outputFolder;

		try {
			outputFolder = new File(args[1]);
			if (outputFolder.exists()) {
				throw new RuntimeException("Folder " + args[1] + " already exists!");
			} else if (!outputFolder.mkdirs()) {
				throw new RuntimeException("Folder " + args[1] + " cannot be created");
			}
		} catch (Exception e) {
			System.err.println("Base file problem: " + e.getMessage());
			System.out.println("");
			printUsageAndExit();
			return;
		}

		File regionDir = new File(outputFolder, "region");
		regionDir.mkdirs();
		System.out.println("Reading Indev level..");
		IndevLevelStorage isl = new IndevLevelStorage(new FileInputStream(indevLevel));
		System.out.println("Writing level.dat..");
		isl.writeMetadata(new File(outputFolder, "level.dat"));
		System.out.println("Creating region..");
		RegionFile regionDest = new RegionFile(new File(regionDir, "r.0.0.mca"));
		int total = isl.getLastChunkX() * isl.getLastChunkZ();
		int pos = 0;
		System.out.println("Converting " + total + " chunks..");
		for (int x = 0; x < isl.getLastChunkX(); x++) {
			for (int z = 0; z < isl.getLastChunkZ(); z++) {
				CompoundTag tag = new CompoundTag();
				CompoundTag levelData = new CompoundTag();
				tag.put("Level", levelData);
				isl.convertToAnvil(x, z, levelData);
				DataOutputStream chunkDataOutputStream = regionDest.getChunkDataOutputStream(x, z);
				NbtIo.write(tag, chunkDataOutputStream);
				chunkDataOutputStream.close();
				System.out.println(String.format("Progress: %.2f%%", 100f * (float) ++pos / (float) total));
			}
		}
		regionDest.close();
		long end = System.currentTimeMillis();
		System.out.println(args[0] + " converted to " + args[1]);
		System.out.println(
				"To load the output world in game, place the \"" + args[1] + "\" folder in your saves folder.");
		System.out.println("Example:");
		System.out.println("\trobocopy " + args[1] + " %appdata%\\.minecraft\\saves\\" + args[1] + " /e");
		System.out.println(String.format("Done! in %.3fms", (float) (end - start) / 1000F));
	}

	private static void printUsageAndExit() {
		System.out.println("Indev to Anvil Map converter mod by hoshinetsu.");
		System.out.println(
				"Based on Map converter for Minecraft, from format \"McRegion\" to \"Anvil\" (c) Mojang AB 2012");
		System.out.println(
				"The mod author disclaims any copyright to the source code. All rights belong to Mojang Studios / formerly Mojang AB.");
		System.out.println("Minecraft is a registred trademark of Mojang Studios.");
		System.out.println("Don't do evil.");
		System.out.println("");
		System.out.println("Usage:");
		System.out.println("\tjava -jar IndevToAnvil.jar <indev save> <world name>");
		System.out.println("Where:");
		System.out.println("\t<indev save>\tThe file name of the Minecraft Indev .mclevel save file");
		System.out.println("\t<world name>\tThe folder name of the Minecraft Anvil output world");
		System.out.println("Example:");
		System.out.println("\tjava -jar IndevToAnvil.jar floating.mclevel indevFloating");
		System.exit(1);
	}
}
