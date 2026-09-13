package com.notegt.client.mixin;

import com.mojang.blaze3d.audio.SoundBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import javax.sound.sampled.AudioFormat;
import java.nio.ByteBuffer;

/**
 * {@link SoundBuffer} 字段访问器 [1.21.11 已验证，Mojang mappings]：
 * <pre>
 *   data        (ByteBuffer, @Nullable，懒上传后置 null)
 *   format      (AudioFormat, final，解码器固定 16-bit 有符号小端)
 *   hasAlBuffer (boolean)
 *   alBuffer    (int)
 * </pre>
 * 运行时 DSP 换缓冲 = discardAlBuffer() + 换 data + hasAlBuffer=false → 下次 getAlBuffer() 重新上传。
 */
@Mixin(SoundBuffer.class)
public interface SoundBufferAccess {

	@Accessor("data")
	ByteBuffer notegt$data();

	@Accessor("data")
	void notegt$setData(ByteBuffer buffer);

	@Accessor("hasAlBuffer")
	boolean notegt$hasAlBuffer();

	@Accessor("hasAlBuffer")
	void notegt$setHasAlBuffer(boolean value);

	@Accessor("format")
	AudioFormat notegt$format();

	@Invoker("discardAlBuffer")
	void notegt$discardAlBuffer();
}
