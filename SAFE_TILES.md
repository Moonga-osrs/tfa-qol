# Safe tiles commands

Enable TFA Qol & mistake tracker, then type one command in the game chat input:

| Command | Highlights |
| --- | --- |
| `::safetiles 1` | Guardian vines: red 3x3 including the vine; mushrooms: orange 5x5. |
| `::safetiles 2` | Bork ground graphics 4395 and active flame residue 62544, red. |
| `::safetiles 3` | Active Dreadnoth jelly blood objects 62609, red. |
| `::safetiles 4` | Active Xarth flame objects 62713, red. |
| `::safetiles p1` | Visible Ocellus floor-crack graphics 4236-4259, red. |
| `::safetiles p2` | Current player quadrant: red 12x12, leaving the observed current safe 2x2 unmarked. |
| `::safetiles p3` | Falling-debris graphics/residue, the bomb tile and active Insolence residue 62703-62705, red. |
| `::safetiles off` | Disable highlights. |

One mode is active at a time. The selection is command-only and lives in memory for the current plugin session. It is not a configuration setting and is not saved to the RuneLite profile. Restarting or disabling the plugin resets it to off. Portal mode uses the 24 currently observed portals and requires five active lanes plus one confirmed inactive lane on both relevant arms. Incomplete, duplicate, ambiguous or transition layouts show no portal highlights. It does not predict subsequent patterns and does not retain previous safe squares. Crossing into another quadrant recomputes the highlighted area.

Highlights follow visible scene objects and graphics, and clear on loading, logout and plugin shutdown. Persistent objects disappear from the overlay when they despawn. Graphic markers use the observed anchor tile; they do not assert that every mechanic has a proven one-tile damage radius. In particular, the full falling-debris damage radius is not established by the recordings.

Additional candidates for later review: Ocellus AoE ground graphic 4270 and charge/explosion graphics 4262-4265. These are intentionally not included in the requested modes yet.
