import com.minecolonies.apiimp.initializer.ModContainerInitializers;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Headless round-trip harness for the extra screen-opening data of every minecolonies menu.
 *
 * Reproduces what fabric-menu-api-v1 does with the `fabric-menu-api-v1:open_screen` custom payload:
 * a prefix (menu id / container id / title) followed by the extended menu type's own stream codec, and
 * a hard requirement that the payload buffer is fully drained once decoding finishes.
 */
public final class ScreenOpeningRoundTrip
{
    private static int failures = 0;

    public static void main(final String[] args)
    {
        // A. the codec itself: it must consume the tail and survive the payload buffer being released.
        codecDrainsPayload("RAW_BUFFER (production)", ModContainerInitializers.ScreenOpeningData.RAW_BUFFER);
        codecDrainsPayload("identity codec (pre-fix, expected to FAIL)", new StreamCodec<>()
        {
            @Override
            public RegistryFriendlyByteBuf decode(final RegistryFriendlyByteBuf buffer)
            {
                return buffer;
            }

            @Override
            public void encode(final RegistryFriendlyByteBuf buffer, final RegistryFriendlyByteBuf value)
            {
                buffer.writeBytes(value);
            }
        }, true);

        // B. per-menu layouts: writer sequence -> reader sequence, values equal and buffer fully drained.
        final BlockPos pos = new BlockPos(12, -47, 3891);
        final BlockPos other = new BlockPos(-9000, 200, 17);

        menu("rack_inv",
          b -> { b.writeBlockPos(pos); b.writeBlockPos(other); },
          b -> List.of(b.readBlockPos(), b.readBlockPos()),
          List.of(pos, other));

        menu("grave_inv",
          b -> b.writeBlockPos(pos),
          b -> List.of(b.readBlockPos()),
          List.of(pos));

        menu("citizen_inv",
          b -> { b.writeVarInt(7); b.writeVarInt(4211); },
          b -> List.of(b.readVarInt(), b.readVarInt()),
          List.of(7, 4211));

        menu("building_inv",
          b -> { b.writeVarInt(7); b.writeBlockPos(pos); },
          b -> List.of(b.readVarInt(), b.readBlockPos()),
          List.of(7, pos));

        menu("crafting_furnace",
          b -> { b.writeBlockPos(pos); b.writeInt(5); },
          b -> List.of(b.readBlockPos(), b.readInt()),
          List.of(pos, 5));

        menu("crafting_brewingstand",
          b -> { b.writeBlockPos(pos); b.writeInt(5); },
          b -> List.of(b.readBlockPos(), b.readInt()),
          List.of(pos, 5));

        menu("crafting_building",
          b -> { b.writeBoolean(true); b.writeBlockPos(pos); b.writeInt(5); },
          b -> List.of(b.readBoolean(), b.readBlockPos(), b.readInt()),
          List.of(true, pos, 5));

        System.out.println(failures == 0 ? "\nALL CHECKS PASSED" : "\n" + failures + " CHECK(S) FAILED");
        System.exit(failures == 0 ? 0 : 1);
    }

    private static void codecDrainsPayload(final String name, final StreamCodec<RegistryFriendlyByteBuf, RegistryFriendlyByteBuf> codec)
    {
        codecDrainsPayload(name, codec, false);
    }

    private static void codecDrainsPayload(final String name,
      final StreamCodec<RegistryFriendlyByteBuf, RegistryFriendlyByteBuf> codec,
      final boolean expectFailure)
    {
        final BlockPos pos = new BlockPos(12, -47, 3891);
        final BlockPos other = new BlockPos(-9000, 200, 17);

        final RegistryFriendlyByteBuf data = buf();
        data.writeBlockPos(pos);
        data.writeBlockPos(other);
        final int dataLength = data.readableBytes();

        // server side: fabric writes its own prefix, then the menu type's codec appends the extra data.
        final RegistryFriendlyByteBuf payload = buf();
        payload.writeUtf("fabric prefix stand-in");
        codec.encode(payload, data);

        // client side: fabric reads its prefix back, then the menu type's codec takes the tail.
        payload.readUtf();
        final RegistryFriendlyByteBuf decoded = codec.decode(payload);
        final int leftOver = payload.readableBytes();

        payload.release();

        boolean ok = leftOver == 0;
        String detail = "leftover=" + leftOver + " (expected 0, data was " + dataLength + " bytes)";
        if (ok)
        {
            // the payload buffer is gone by the time the container factory runs -- decoded must be independent.
            try
            {
                ok = decoded.readBlockPos().equals(pos) && decoded.readBlockPos().equals(other) && decoded.readableBytes() == 0;
                detail += ", decoded content " + (ok ? "matches" : "DOES NOT match");
            }
            catch (final RuntimeException e)
            {
                ok = false;
                detail += ", reading decoded threw " + e;
            }
        }

        report(name, expectFailure != ok, detail + (expectFailure ? " [failure expected]" : ""));
    }

    private static void menu(final String name,
      final Consumer<RegistryFriendlyByteBuf> writer,
      final Function<RegistryFriendlyByteBuf, List<?>> reader,
      final List<?> expected)
    {
        final RegistryFriendlyByteBuf data = buf();
        writer.accept(data);
        final int written = data.readableBytes();

        // through the real codec, exactly as the payload carries it
        final RegistryFriendlyByteBuf payload = buf();
        ModContainerInitializers.ScreenOpeningData.RAW_BUFFER.encode(payload, data);
        final RegistryFriendlyByteBuf decoded = ModContainerInitializers.ScreenOpeningData.RAW_BUFFER.decode(payload);
        final int leftOver = payload.readableBytes();
        payload.release();

        final List<?> got;
        try
        {
            got = reader.apply(decoded);
        }
        catch (final RuntimeException e)
        {
            report(name, false, "reader threw " + e);
            return;
        }
        final int unread = decoded.readableBytes();

        final List<String> problems = new ArrayList<>();
        if (leftOver != 0)
        {
            problems.add("payload not drained: " + leftOver + " byte(s) left");
        }
        if (unread != 0)
        {
            problems.add("reader left " + unread + " byte(s) unread");
        }
        if (!got.equals(expected))
        {
            problems.add("values differ: expected " + expected + " got " + got);
        }
        report(name, problems.isEmpty(), problems.isEmpty() ? written + " bytes, drained, values match" : String.join("; ", problems));
    }

    private static RegistryFriendlyByteBuf buf()
    {
        return new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
    }

    private static void report(final String name, final boolean ok, final String detail)
    {
        if (!ok)
        {
            failures++;
        }
        System.out.printf("%-45s %s  %s%n", name, ok ? "OK  " : "FAIL", detail);
    }
}
