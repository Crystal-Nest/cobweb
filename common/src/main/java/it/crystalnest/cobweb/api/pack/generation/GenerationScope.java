package it.crystalnest.cobweb.api.pack.generation;

import it.crystalnest.cobweb.api.pack.generation.lifecycle.LoadKind;
import it.crystalnest.cobweb.api.pack.generation.lifecycle.Phase;
import it.crystalnest.cobweb.api.pack.generation.lifecycle.Stage;
import it.crystalnest.cobweb.api.pack.query.PackQueries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.flag.FeatureFlagSet;

/**
 * Groups the stable identity, lifecycle window and native selection of one generation callback.
 *
 * @param pack logical ID of the callback's owning pack.
 * @param phase active native generation phase.
 * @param stage active stage within the phase.
 * @param loadKind full load or reload.
 * @param packs captured pack selection and priority.
 * @param features active feature flags.
 */
record GenerationScope(Identifier pack, Phase phase, Stage stage, LoadKind loadKind, PackQueries packs, FeatureFlagSet features) {}
