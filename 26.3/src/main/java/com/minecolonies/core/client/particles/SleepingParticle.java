package com.minecolonies.core.client.particles;

import com.minecolonies.api.util.constant.Constants;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;

/**
 * Custom particle for sleeping.
 * <p>
 * PORT-26.2: {@code TextureSheetParticle} is gone; textured particles extend {@link SingleQuadParticle}, which takes
 * the sprite in the constructor and picks its draw layer through {@link SingleQuadParticle#getLayer()} instead of the
 * old {@code getRenderType()}. {@code ParticleProvider#createParticle} also gained a {@link RandomSource} parameter.
 */
public class SleepingParticle extends SingleQuadParticle
{
    /**
     * Spawn coords
     */
    private final double coordX;
    private final double coordY;
    private final double coordZ;

    /**
     * The light level of the particle
     */
    private static final int LIGHT_LEVEL = 15 << 20 | 15 << 4;

    /**
     * The resourcelocation for the sleeping image.
     */
    public static final Identifier SLEEPING_TEXTURE = Identifier.fromNamespaceAndPath(Constants.MOD_ID, "particle/sleeping");

    /**
     * Sprites of this particle, kept so the sprite can follow the age like it used to.
     */
    private final SpriteSet spriteSet;

    public SleepingParticle(
      final SpriteSet spriteSet,
      final ClientLevel worldIn,
      final double xCoordIn,
      final double yCoordIn,
      final double zCoordIn,
      final double xSpeedIn,
      final double ySpeedIn,
      final double zSpeedIn)
    {
        super(worldIn, xCoordIn, yCoordIn, zCoordIn, xSpeedIn, ySpeedIn, zSpeedIn, spriteSet.get(RandomSource.create()));

        this.spriteSet = spriteSet;
        setSpriteFromAge(spriteSet);
        this.xd = xSpeedIn * 0.5;
        this.yd = ySpeedIn;
        this.zd = zSpeedIn;
        this.coordX = xCoordIn;
        this.coordY = yCoordIn;
        this.coordZ = zCoordIn;
        this.xo = xCoordIn;
        this.yo = yCoordIn;
        this.zo = zCoordIn;
        this.x = this.xo;
        this.y = this.yo;
        this.z = this.zo;
        // Slight color variance
        final float f = this.random.nextFloat() * 0.6F + 0.4F;
        this.rCol = 0.9F * f;
        this.gCol = 0.9F * f;
        this.bCol = f;
        // particles max age in ticks, random causes them to appear a bit more dynamic, as they get faster/slower with shorter/longer lifetime
        this.lifetime = (int) (Math.random() * 30.0D) + 40;
        // starting scale to fit
        this.quadSize = (float) ((0.8 * Math.sin(0) + 1.3) * 0.1);
    }

    /**
     * Updates the particles, setting new position/scale
     */
    @Override
    public void tick()
    {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;

        final float f = (float) this.age / (float) this.lifetime;

        // Scale smaller/bigger in a similar rate to snoring
        quadSize = (float) ((0.8 * Math.sin(f * 4) + 1.3) * 0.1);

        // Moves the particle in relation to movespeed and age
        this.x = this.coordX + this.xd * f;
        this.y = this.coordY + this.yd * f;
        this.z = this.coordZ + this.zd * f;

        setSpriteFromAge(this.spriteSet);

        if (this.age++ >= this.lifetime)
        {
            this.remove();
        }
    }

    @Override
    protected int getLightCoords(final float partialTick)
    {
        return LIGHT_LEVEL;
    }

    @Override
    protected SingleQuadParticle.Layer getLayer()
    {
        return SingleQuadParticle.Layer.TRANSLUCENT;
    }

    public static class Factory implements ParticleProvider<SimpleParticleType>
    {
        private final SpriteSet spriteSet;

        public Factory(final SpriteSet spriteSet)
        {
            this.spriteSet = spriteSet;
        }

        @Override
        public Particle createParticle(
          final SimpleParticleType particleType,
          final ClientLevel world,
          final double x,
          final double y,
          final double z,
          final double xSpeed,
          final double ySpeed,
          final double zSpeed,
          final RandomSource random)
        {
            return new SleepingParticle(spriteSet, world, x, y, z, xSpeed, ySpeed, zSpeed);
        }
    }
}
