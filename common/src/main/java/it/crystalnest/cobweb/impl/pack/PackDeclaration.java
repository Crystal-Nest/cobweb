package it.crystalnest.cobweb.impl.pack;

import it.crystalnest.cobweb.api.pack.dependency.PackDependencySettings;
import it.crystalnest.cobweb.api.pack.generation.lifecycle.PackLoadOutcome;
import it.crystalnest.cobweb.api.pack.presentation.PackIcon;
import it.crystalnest.cobweb.api.pack.presentation.PackPresentation;
import it.crystalnest.cobweb.api.pack.presentation.PackPresentationContext;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Identity, dependencies, presentation and load observer of a managed pack.
 *
 * @param id logical pack identifier.
 * @param dependencies explicit Java overrides.
 * @param observer optional native outcome observer.
 * @param icon explicit base icon, if any.
 * @param presentation optional editor presentation callback.
 * @param explicitTitle whether Java supplies the base title.
 * @param explicitDescription whether Java supplies the base description.
 */
@ApiStatus.Internal
public record PackDeclaration(
  Identifier id,
  PackDependencySettings dependencies,
  @Nullable Consumer<PackLoadOutcome> observer,
  @Nullable PackIcon icon,
  @Nullable Function<PackPresentationContext, PackPresentation> presentation,
  boolean explicitTitle,
  boolean explicitDescription
) {}
