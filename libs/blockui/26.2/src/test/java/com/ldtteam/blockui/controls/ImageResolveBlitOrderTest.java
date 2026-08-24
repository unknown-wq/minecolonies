package com.ldtteam.blockui.controls;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.io.InputStream;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.CodeElement;
import java.lang.classfile.MethodModel;
import java.lang.classfile.instruction.InvokeInstruction;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code Image#resolveBlit} has to ask the gui atlas before it asks the texture manager, and this test asserts exactly
 * that, on the compiled bytecode.
 * <p>
 * A sprite id has no stand-alone file behind it - {@code minecolonies:building/scarecrow/north} lives inside an atlas,
 * there is no {@code textures/building/scarecrow/north.png}. {@code OutOfJarTexture#assertLoadedDefaultManagers} hands
 * the id straight to {@code TextureManager#getTexture}, which loads the file, fails, and logs
 * {@code "Missing resource <id> referenced from <id>"}. While that call sat above the atlas lookup and ran
 * unconditionally, every correctly stitched sprite drawn through {@code Image} reported itself as missing - a false
 * warning per sprite, with nothing wrong anywhere.
 * <p>
 * Nothing here can be checked at runtime: {@code Image#resolveBlit} needs a live {@code Minecraft}, and {@code Pane}
 * cannot even be loaded headless ({@code Cursor.<clinit>} collapses all {@code CursorTypes} into
 * {@code CursorType.DEFAULT} without GLFW and dies on a duplicate map key). The call order is a static property of the
 * method, so it is read statically - the same way the false positive was originally proven. The class bytes are pulled
 * through the class loader as a resource rather than through {@code Image.class}, so this test does not load the class
 * at all.
 */
public class ImageResolveBlitOrderTest
{
    private static final String IMAGE_CLASS = "com/ldtteam/blockui/controls/Image.class";
    private static final String ATLAS_LOOKUP = "com/ldtteam/blockui/util/texture/GuiAtlasLookup#resolveSprite";
    private static final String TEXTURE_MANAGER_LOOKUP = "com/ldtteam/blockui/util/texture/OutOfJarTexture#assertLoadedDefaultManagers";

    /**
     * @return every method call inside the five-argument {@code Image#resolveBlit}, in bytecode order
     */
    private static List<String> callsInResolveBlit() throws IOException
    {
        final byte[] classBytes;
        try (InputStream in = ImageResolveBlitOrderTest.class.getClassLoader().getResourceAsStream(IMAGE_CLASS))
        {
            assertNotNull(in, IMAGE_CLASS + " is expected on the test classpath");
            classBytes = in.readAllBytes();
        }

        final ClassModel image = ClassFile.of().parse(classBytes);
        for (final MethodModel method : image.methods())
        {
            if (!"resolveBlit".equals(method.methodName().stringValue()) || method.methodTypeSymbol().parameterCount() != 5)
            {
                continue;
            }

            final List<String> calls = new ArrayList<>();
            for (final CodeElement element : method.code().orElseThrow())
            {
                if (element instanceof final InvokeInstruction invoke)
                {
                    calls.add(invoke.owner().asInternalName() + "#" + invoke.name().stringValue());
                }
            }
            return calls;
        }

        throw new AssertionError("Image#resolveBlit(Identifier, int, int, int, int) not found - was it renamed?");
    }

    @Test
    public void theAtlasIsConsultedBeforeTheTextureManager() throws IOException
    {
        final List<String> calls = callsInResolveBlit();
        final int atlasAt = calls.indexOf(ATLAS_LOOKUP);
        final int textureManagerAt = calls.indexOf(TEXTURE_MANAGER_LOOKUP);

        assertTrue(atlasAt >= 0, () -> ATLAS_LOOKUP + " is not called at all, calls were " + calls);
        assertTrue(textureManagerAt >= 0, () -> TEXTURE_MANAGER_LOOKUP + " is not called at all, calls were " + calls);
        assertTrue(atlasAt < textureManagerAt,
            () -> "the gui atlas must be consulted before the texture manager, otherwise every atlas sprite is reported as a"
                + " missing resource; calls were " + calls);
    }

    @Test
    public void theMissingResourceDiagnosticIsStillReachable()
    {
        // the point of the fix is to move the texture-manager call onto the stand-alone-texture branch, not to delete
        // it: a resource that really is absent and really is not a sprite has to keep reporting itself
        assertTrue(assertDoesNotThrowIo().contains(TEXTURE_MANAGER_LOOKUP),
            "Image#resolveBlit must still reach the texture manager for non-atlas textures");
    }

    private static List<String> assertDoesNotThrowIo()
    {
        try
        {
            return callsInResolveBlit();
        }
        catch (final IOException e)
        {
            throw new AssertionError("could not read " + IMAGE_CLASS, e);
        }
    }
}
