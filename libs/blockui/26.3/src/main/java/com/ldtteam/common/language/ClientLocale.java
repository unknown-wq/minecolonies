package com.ldtteam.common.language;

import net.minecraft.client.Minecraft;

public class ClientLocale
{
    /**
     * @return the language code the player has selected, or null while there is no way to know it yet
     */
    public static String getLocale()
    {
        // Trust me, Minecraft.getInstance() can be null, when you run Data Generators!
        // And in 26.2 it can just as well be non-null with #options still unset: mod entrypoints are invoked
        // from inside Minecraft.<init>, which runs before the options are read. Testing the instance alone was
        // one field too shallow, so everything that asked for the locale during mod init died here.
        final Minecraft mc = Minecraft.getInstance();
        return mc == null || mc.options == null ? null : mc.options.languageCode;
    }
}
