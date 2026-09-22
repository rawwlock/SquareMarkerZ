package dev.squaremarkerz.squaremap;

import dev.squaremarkerz.model.MarkerRecord;
import java.util.Collection;
import xyz.jpenilla.squaremap.api.Key;

/**
 * Bridges {@code SquaremapIntegration} to the marker data it needs when (re)building a layer, without
 * creating a hard compile-time dependency on {@code MarkerManager}.
 */
public interface MarkerSource {

    Collection<MarkerRecord> markersInWorldAndSet(String worldName, String setId);

    /**
     * The currently registered squaremap icon key for a marker, or {@code null} if its icon has not
     * finished resolving yet (it will be added to the layer once it does).
     */
    Key iconKeyFor(String markerId);
}
