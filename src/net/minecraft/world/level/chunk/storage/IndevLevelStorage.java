package net.minecraft.world.level.chunk.storage;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;

import com.mojang.nbt.CompoundTag;
import com.mojang.nbt.DoubleTag;
import com.mojang.nbt.FloatTag;
import com.mojang.nbt.ListTag;
import com.mojang.nbt.NbtIo;
import com.mojang.nbt.ShortTag;

import net.minecraft.world.level.chunk.DataLayer;

/**
 * Modded by hoshinetsu
 * Copyright Mojang AB.
 * 
 * Don't do evil.
 */

public class IndevLevelStorage {

	public int width;
	public int height;
	public int depth;

	private byte[] blocks;
	private byte[] light;

	public long ticks;
	public long createTime;

	public String name;

	public int spawnX, spawnY, spawnZ;

	private CompoundTag player;

	private HashMap<Integer, CompoundTag> entityMap = new HashMap<Integer, CompoundTag>();

	@SuppressWarnings("unchecked")
	public IndevLevelStorage(InputStream is) throws IOException {
		name = "Indev Level";
		CompoundTag main = NbtIo.readCompressed(is);
		ticks = main.getCompound("Environment").getShort("TimeOfDay");
		createTime = main.getCompound("About").getLong("CreatedOn");

		CompoundTag map = main.getCompound("Map");
		ListTag<ShortTag> spawn = (ListTag<ShortTag>) map.getList("Spawn");
		spawnX = spawn.get(0).data;
		spawnY = spawn.get(1).data;
		spawnZ = spawn.get(2).data;

		width = map.getShort("Width");
		depth = map.getShort("Height");
		height = map.getShort("Length");

		blocks = map.getByteArray("Blocks");
		light = map.getByteArray("Data");

		int lx = getLastChunkX();
		int lz = getLastChunkZ();

		for (int x = 0; x < lx; x++) {
			for (int z = 0; z < lz; z++) {
				CompoundTag tag = new CompoundTag();
				ListTag<CompoundTag> entities = new ListTag<CompoundTag>();
				ListTag<CompoundTag> tileEntities = new ListTag<CompoundTag>();
				tag.put("Entities", entities);
				tag.put("TileEntities", tileEntities);
				entityMap.put((x & 0xFFFF) << 16 | (z & 0xFFFF), tag);
			}
		}

		ListTag<CompoundTag> entities = (ListTag<CompoundTag>) main.getList("Entities");
		for (int i = 0; i < entities.size(); i++) {
			CompoundTag entity = entities.get(i);
			ListTag<FloatTag> motion = (ListTag<FloatTag>) entity.getList("Motion");
			ListTag<FloatTag> pos = (ListTag<FloatTag>) entity.getList("Pos");
			entity.remove("Motion");
			entity.remove("Pos");
			ListTag<DoubleTag> motionD = new ListTag<DoubleTag>();
			ListTag<DoubleTag> posD = new ListTag<DoubleTag>();
			for (int x = 0; x < motion.size(); x++) {
				motionD.add(new DoubleTag("", (double) motion.get(x).data));
			}

			for (int x = 0; x < pos.size(); x++) {
				posD.add(new DoubleTag("", (double) pos.get(x).data));
			}
			entity.put("Motion", motionD);
			entity.put("Pos", posD);
			if (entity.getString("id").equals("LocalPlayer")) {
				System.out.println("Found player!");
				player = entity;
				continue;
			}
			int x = (int) Math.floor(posD.get(0).data / 16D);
			int z = (int) Math.floor(posD.get(2).data / 16D);
			if (x < 0)
				x = 0;
			else if (x > lx)
				x = lx;
			if (z < 0)
				z = 0;
			else if (z > lz)
				z = lz;

			CompoundTag tag = entityMap.get((x & 0xFFFF) << 16 | (z & 0xFFFF));
			((ListTag<CompoundTag>) tag.getList("Entities")).add(entity);
		}

		ListTag<CompoundTag> tileEntities = (ListTag<CompoundTag>) main.getList("TileEntities");
		for (int i = 0; i < tileEntities.size(); i++) {
			CompoundTag entity = tileEntities.get(i);
			int pos = entity.getInt("Pos");
			int x = pos & 0x3FF;
			int y = (pos >> 10) & 0x3FF;
			int z = (pos >> 20) & 0x3FF;
			entity.putInt("x", x);
			entity.putInt("y", y);
			entity.putInt("z", z);
			int cx = x / 16;
			int cz = z / 16;
			CompoundTag tag = entityMap.get((cx & 0xFFFF) << 16 | (cz & 0xFFFF));
			((ListTag<CompoundTag>) tag.getList("TileEntities")).add(entity);
		}
	}

	public int getLastChunkX() {
		return width / 16;
	}

	public int getLastChunkZ() {
		return height / 16;
	}

	public void convertToAnvil(int cx, int cz, CompoundTag tag) {
		tag.putInt("xPos", cx);
		tag.putInt("zPos", cz);
		tag.putLong("LastUpdate", ticks);
		int[] newHeight = new int[16 * 16];
		for (int i = 0; i < newHeight.length; i++) {
			int bx = cx * 16 + (i % 16);
			int bz = cz * 16 + (i / 16);
			int by = depth - 1;
			while (by > 0) {
				int pos = (by * height + bz) * width + bx;
				if (blocks[pos] != 0) {
					newHeight[i] = by + 1;
					break;
				}
				by--;
			}
		}
		tag.putIntArray("HeightMap", newHeight);
		tag.putBoolean("TerrainPopulated", true);

		ListTag<CompoundTag> sectionTags = new ListTag<CompoundTag>("Sections");
		for (int yBase = 0; yBase < (depth / 16); yBase++) {
			// build section
			byte[] blocks = new byte[16 * 16 * 16];
			DataLayer dataValues = new DataLayer(blocks.length, 4);
			DataLayer skyLight = new DataLayer(blocks.length, 4);
			DataLayer blockLight = new DataLayer(blocks.length, 4);

			for (int x = 0; x < 16; x++) {
				for (int y = 0; y < 16; y++) {
					for (int z = 0; z < 16; z++) {
						int bx = cx * 16 + x;
						int by = (y + (yBase << 4));
						int bz = cz * 16 + z;
						int i = (by * height + bz) * width + bx;
						int block = this.blocks[i];
						if (by == 0)
							block = 7; // patch bedrock
						blocks[(y << 8) | (z << 4) | x] = (byte) (block & 0xFF);
						dataValues.set(x, y, z, (light[i] >> 4) & 0x0F);
						skyLight.set(x, y, z, light[i] & 0x0F);
						int blocklight = 0;
						if (block == 10 || block == 11)
							blocklight = 15; // lava light patch
						else if (block == 50)
							blocklight = 14; // torch light patch
						blockLight.set(x, y, z, blocklight);
					}
				}
			}

			CompoundTag sectionTag = new CompoundTag();

			sectionTag.putByte("Y", (byte) (yBase & 0xff));
			sectionTag.putByteArray("Blocks", blocks);
			sectionTag.putByteArray("Data", dataValues.data);
			sectionTag.putByteArray("SkyLight", skyLight.data);
			sectionTag.putByteArray("BlockLight", blockLight.data);

			sectionTags.add(sectionTag);
		}
		tag.put("Sections", sectionTags);
		CompoundTag entTag = entityMap.get((cx & 0xFFFF) << 16 | (cz & 0xFFFF));
		tag.put("Entities", entTag.getList("Entities"));
		tag.put("TileEntities", entTag.getList("TileEntities"));
	}

	public void writeMetadata(File file) throws IOException {
		CompoundTag ct = new CompoundTag();
		CompoundTag data = new CompoundTag();
		ct.put("Data", data);
		data.putInt("GameType", 0);
		data.putString("generatorName", "flat");
		data.putInt("generatorVersion", 0);
		data.putString("LevelName", name);
		data.putLong("RandomSeed", createTime);
		data.putInt("SpawnX", spawnX);
		data.putInt("SpawnY", spawnY);
		data.putInt("SpawnZ", spawnZ);
		data.putInt("version", 0x4abd);
		data.putLong("Time", ticks);
		data.putLong("LastPlayed", createTime);
		if (player != null)
			data.putCompound("Player", player);
		NbtIo.writeCompressed(ct, new FileOutputStream(file));
	}

}
