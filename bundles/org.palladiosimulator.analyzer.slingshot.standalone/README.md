# Slingshot standalone

Runs a Slingshot simulation on a plain class path: no Eclipse, no OSGi
framework, no workspace. The bundle is a regular plug-in as well, so it builds
with Tycho like the other bundles, but it is not part of any feature.

## How it works

| Step | Eclipse launch | Standalone |
|---|---|---|
| Extension registry | Equinox | `ClasspathExtensionRegistry`: `EcorePlugin.ExtensionProcessor` reads every `plugin.xml` on the class path |
| EMF packages, URI mappings | OSGi + EMF plug-in | `EmfStandalone`: `platform:/plugin/<bundle>/` points to the jar, `uri_mapping` targets are resolved |
| Slingshot instance | `Slingshot.start(BundleContext)` | `Slingshot.startStandalone()` / `stopStandalone()` |
| Models | launch tabs + `LoadModelIntoBlackboardJob` | the same jobs, model files from the command line |
| Simulation | `SimulationJob` | the same `SimulationJob` |
| Results | EDP2 | `CsvRecorder` ("Slingshot Standalone CSV"), one file per measuring point and metric |

Launch attributes use the same keys and value types as a `.launch` file:
`String` for text attributes, `Boolean` for boolean ones
(`SimuComConfig.USE_FIXED_SEED` etc.).

## Command line

```
java -cp <all bundle jars> org.palladiosimulator.analyzer.slingshot.standalone.SlingshotStandalone \
  --model default.allocation --model my.usagemodel --model my.monitorrepository \
  --simTime 20000 --seed 0 --out results
```

| Option | Meaning |
|---|---|
| `--model <file>` | model file, repeatable (allocation, usage model, monitor repository, ...) |
| `--simTime <s>` | maximum simulation time |
| `--maxMeasurements <n>` | maximum number of measurements (`-1` = unlimited) |
| `--seed <n>` | fixed seed; the six seeds are `n` ... `n+5` |
| `--out <dir>` | directory for the CSV files (default `results`) |
| `--project <name>=<dir>` | maps `platform:/resource/<name>/` to a directory |
| `--set <key>=<value>` | any other launch attribute (`true`/`false` become `Boolean`) |

The class path needs the Slingshot bundles and their dependencies as jars.
`org.palladiosimulator.analyzer.slingshot.behavior.spd.adjustment` needs the
QVTo transformations of `org.palladiosimulator.spd.semantic.transformations`;
without that bundle, the simulation needs a binding for
`org.palladiosimulator.semanticspd.Configuration` from another module.

## Reproducibility

With `--seed`, two runs of the same models produce byte-identical CSV files,
also when several runs share one JVM (`SlingshotStandalone.run` can be called
repeatedly).
