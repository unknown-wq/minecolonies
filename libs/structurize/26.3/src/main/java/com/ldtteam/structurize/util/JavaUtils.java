package com.ldtteam.structurize.util;

import com.ldtteam.structurize.api.Log;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

public class JavaUtils
{
    private JavaUtils()
    {
    }

    /**
     * Delete directory and all files and directories in it.
     * @param path the path of the file to delete.
     * @return true if successful.
     */
    public static boolean deleteDirectory(Path path)
    {
        if (!Files.exists(path))
        {
            return true;
        }

        try
        {
            try (final Stream<Path> paths = Files.list(path))
            {
                paths.forEach(child ->
                {
                    if (Files.isDirectory(child))
                    {
                        deleteDirectory(child);
                    }

                    try
                    {
                        Files.deleteIfExists(child);
                    }
                    catch (Exception e)
                    {
                        Log.getLogger().warn("Failed deleting: " + child, e);
                    }
                });
            }
        }
        catch (IOException e)
        {
            Log.getLogger().warn("Failed deleting: " + path, e);
            return false;
        }

        try
        {
            return Files.deleteIfExists(path);
        }
        catch (IOException e)
        {
            Log.getLogger().warn("Failed deleting: " + path, e);
            return false;
        }
    }

}
