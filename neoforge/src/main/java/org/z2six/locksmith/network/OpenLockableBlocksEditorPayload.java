// MainFile: neoforge/src/main/java/org/z2six/locksmith/network/OpenLockableBlocksEditorPayload.java
package org.z2six.locksmith.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.slf4j.Logger;
import org.z2six.locksmith.Constants;
import org.z2six.locksmith.render.profile.LockTargetType;
import org.z2six.locksmith.render.profile.LockTransform;
import org.z2six.locksmith.render.profile.LockableBlockEntry;
import org.z2six.locksmith.render.profile.LockableBlockValidationResult;
import org.z2six.locksmith.render.profile.LockableBlockValidationStatus;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public record OpenLockableBlocksEditorPayload(
        List<LockableBlockEntry> entries,
        List<LockableBlockValidationResult> validationResults
) implements CustomPacketPayload {
    private static final Logger LOG = Constants.LOG;

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "open_lockable_blocks_editor");
    public static final Type<OpenLockableBlocksEditorPayload> TYPE = new Type<>(ID);

    public static final StreamCodec<FriendlyByteBuf, OpenLockableBlocksEditorPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, msg) -> {
                        writeEntries(buf, msg == null ? List.of() : msg.entries);
                        writeValidationResults(buf, msg == null ? List.of() : msg.validationResults);
                    },
                    buf -> new OpenLockableBlocksEditorPayload(readEntries(buf), readValidationResults(buf))
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OpenLockableBlocksEditorPayload msg, IPayloadContext ctx) {
        try {
            ctx.enqueueWork(() -> openScreenReflectively(msg));
        } catch (Throwable t) {
            LOG.error("[Locksmith][OpenLockableBlocksEditorPayload] handle failed (non-fatal).", t);
        }
    }

    private static void openScreenReflectively(OpenLockableBlocksEditorPayload msg) {
        try {
            Class<?> mcClass = Class.forName("net.minecraft.client.Minecraft");
            Object mc = mcClass.getMethod("getInstance").invoke(null);
            Class<?> screenClass = Class.forName("org.z2six.locksmith.client.screen.LockableBlocksEditorScreen");
            Constructor<?> ctor = screenClass.getConstructor(List.class, List.class);
            Object screen = ctor.newInstance(
                    msg == null ? List.of() : msg.entries,
                    msg == null ? List.of() : msg.validationResults
            );
            Method setScreen = mcClass.getMethod("setScreen", Class.forName("net.minecraft.client.gui.screens.Screen"));
            setScreen.invoke(mc, screen);
        } catch (Throwable t) {
            LOG.error("[Locksmith][OpenLockableBlocksEditorPayload] Failed to open editor screen (non-fatal).", t);
        }
    }

    static void writeEntries(FriendlyByteBuf buf, List<LockableBlockEntry> entries) {
        List<LockableBlockEntry> safe = entries == null ? List.of() : entries;
        buf.writeVarInt(safe.size());
        for (LockableBlockEntry entry : safe) {
            ResourceLocation blockId = entry == null ? null : entry.blockId();
            LockTargetType type = entry == null ? LockTargetType.GENERIC : entry.type();
            LockTransform t = entry == null ? LockTransform.doorDefault() : entry.transform().clamped();
            buf.writeUtf(blockId == null ? "" : blockId.toString(), 256);
            buf.writeByte(type == null ? LockTargetType.GENERIC.id : type.id);
            buf.writeBoolean(entry != null && entry.enabled());
            buf.writeBoolean(entry != null && entry.builtinDefault());
            buf.writeDouble(t.offsetX());
            buf.writeDouble(t.offsetY());
            buf.writeDouble(t.offsetZ());
            buf.writeFloat(t.rotX());
            buf.writeFloat(t.rotY());
            buf.writeFloat(t.rotZ());
            buf.writeFloat(t.scale());
            buf.writeDouble(t.hingeNudgeLeft());
            buf.writeDouble(t.hingeNudgeRight());
            buf.writeDouble(t.doubleNudgeX());
        }
    }

    static List<LockableBlockEntry> readEntries(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        ArrayList<LockableBlockEntry> out = new ArrayList<>(Math.max(0, count));
        for (int i = 0; i < count; i++) {
            ResourceLocation blockId = ResourceLocation.tryParse(buf.readUtf(256));
            LockTargetType type = LockTargetType.fromId(buf.readByte());
            boolean enabled = buf.readBoolean();
            boolean builtinDefault = buf.readBoolean();
            LockTransform transform = new LockTransform(
                    buf.readDouble(),
                    buf.readDouble(),
                    buf.readDouble(),
                    buf.readFloat(),
                    buf.readFloat(),
                    buf.readFloat(),
                    buf.readFloat(),
                    buf.readDouble(),
                    buf.readDouble(),
                    buf.readDouble()
            );
            out.add(new LockableBlockEntry(blockId, type, transform, builtinDefault, enabled));
        }
        return out;
    }

    static void writeValidationResults(FriendlyByteBuf buf, List<LockableBlockValidationResult> results) {
        List<LockableBlockValidationResult> safe = results == null ? List.of() : results;
        buf.writeVarInt(safe.size());
        for (LockableBlockValidationResult result : safe) {
            ResourceLocation blockId = result == null ? null : result.blockId();
            LockableBlockValidationStatus status = result == null ? LockableBlockValidationStatus.INVALID_ID : result.status();
            buf.writeUtf(blockId == null ? "" : blockId.toString(), 256);
            buf.writeVarInt(status == null ? LockableBlockValidationStatus.INVALID_ID.ordinal() : status.ordinal());
            buf.writeUtf(result == null || result.message() == null ? "" : result.message(), 512);
        }
    }

    static List<LockableBlockValidationResult> readValidationResults(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        ArrayList<LockableBlockValidationResult> out = new ArrayList<>(Math.max(0, count));
        LockableBlockValidationStatus[] statuses = LockableBlockValidationStatus.values();
        for (int i = 0; i < count; i++) {
            ResourceLocation blockId = ResourceLocation.tryParse(buf.readUtf(256));
            int ordinal = buf.readVarInt();
            LockableBlockValidationStatus status = ordinal >= 0 && ordinal < statuses.length
                    ? statuses[ordinal]
                    : LockableBlockValidationStatus.INVALID_ID;
            String message = buf.readUtf(512);
            out.add(new LockableBlockValidationResult(blockId, status, message));
        }
        return out;
    }
}
