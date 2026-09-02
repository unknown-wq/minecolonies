package com.ldtteam.structurize.client.model;

/**
 * Simple loader to create {@code OverlaidGeometry}.
 *
 * <p>Port note (26.2): {@code IGeometryLoader} is NeoForge-only and there is no "model loader keyed by the
 * JSON {@code loader} field" concept left; Fabric replaces it with
 * {@code ModelLoadingPlugin.Context#modifyBlockModelOnLoad/AfterBake}. Registration used to live at
 * {@code event/ClientLifecycleSubscriber.java:76} and is disabled there too.</p>
 */
// TODO(port-26.2): DISABLED — IGeometryLoader removed; JSON key "loader" in
//  assets/structurize/models/block/blocktagsubstitution.json is now inert (vanilla ignores unknown keys)
public final class OverlaidModelLoader
{
    private OverlaidModelLoader()
    {
    }

    /*
    public class OverlaidModelLoader implements IGeometryLoader<OverlaidGeometry>
    {
        @Override
        public OverlaidGeometry read(JsonObject jsonObject, JsonDeserializationContext deserializationContext) throws JsonParseException
        {
            final String parent = jsonObject.get("parent").getAsString();
            return new OverlaidGeometry(Identifier.parse(parent));
        }
    }
    */
}
