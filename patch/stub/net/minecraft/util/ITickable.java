package net.minecraft.util;

/**
 * 编译用桩：真实类由 Minecraft/Forge 在运行时提供（SRG 名）。
 * 仅用于让 GTCECompat 能以正确的字节码引用调用 ITickable.update()。
 */
public interface ITickable {
    void func_73660_a();
}
