package com.minecolonies.core.mixin;

import com.minecolonies.core.client.assetfetch.FetchedAssetsSource;
import net.minecraft.client.resources.ClientPackSource;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.RepositorySource;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Appends the {@link FetchedAssetsSource} to the client's resource pack repository.
 *
 * <p>This is the mod's only mixin. It exists because the assets MineColonies used to ship are downloaded at
 * runtime into a directory outside the jar, and no public API can add a pack from an arbitrary directory:
 * Fabric API's {@code ResourceLoader.registerBuiltinPack} only serves packs inside a mod jar. Fabric API
 * solves the identical problem the identical way — see {@code PackRepositoryMixin.construct} in
 * {@code fabric-resource-loader-v1}, which this class is shaped after, down to the
 * {@code @Shadow @Final @Mutable} on the source set and the {@code @Inject} at the constructor's RETURN.</p>
 *
 * <p>{@code PackRepository.sources} is assigned {@code ImmutableSet.copyOf(sources)} by the constructor, so
 * it is replaced with a mutable {@link LinkedHashSet} copy before the new source is added. Insertion order
 * is preserved; it does not decide pack precedence anyway — {@code PackSelectionConfig} does, and this
 * source's pack pins itself to the bottom.</p>
 *
 * <p>Registered in the {@code client} section of {@code minecolonies.mixins.json}, so it never loads on a
 * dedicated server. That is not merely tidiness: {@code PackRepository} is a common class that the server
 * also uses for its data packs, and the fetched pack is client resources only. On the client, the same
 * class backs both the resource pack repository and the integrated server's data pack repository, so the
 * injection additionally checks for a {@link ClientPackSource} — present in exactly the one repository
 * {@code Minecraft} builds for client resources — before touching anything.</p>
 */
@Mixin(PackRepository.class)
public abstract class PackRepositoryMixin
{
    @Shadow
    @Final
    @Mutable
    private Set<RepositorySource> sources;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void minecolonies$addFetchedAssetsSource(final RepositorySource[] constructorSources, final CallbackInfo ci)
    {
        boolean clientResources = false;
        for (final RepositorySource source : this.sources)
        {
            if (source instanceof ClientPackSource)
            {
                clientResources = true;
                break;
            }
        }

        if (!clientResources)
        {
            return;
        }

        this.sources = new LinkedHashSet<>(this.sources);
        this.sources.add(new FetchedAssetsSource());
    }
}
