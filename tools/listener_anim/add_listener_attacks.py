"""Adds the Dead Air 3.0 attack animations to the Listener animation file (idempotent)."""
import json
import os

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
PATH = os.path.join(ROOT, "src", "main", "resources", "assets", "dead_air", "animations", "entity",
                    "the_listener.animation.json")
BELLS = ["bell_top", "bell_upper_left", "bell_upper_right", "bell_lower_left", "bell_lower_right"]


def main():
    with open(PATH) as f:
        data = json.load(f)
    anims = data["animations"]

    claw = {"animation_length": 0.9, "bones": {
        "torso": {"rotation": {"0.0": [0, 0, 0], "0.3": [4, 18, 0], "0.4": [-12, -22, 0], "0.9": [0, 0, 0]}},
        "head": {"rotation": {"0.0": [0, 0, 0], "0.3": [6, 10, 0], "0.4": [-8, -12, 0], "0.9": [0, 0, 0]}},
        "arm_right": {"rotation": {"0.0": [0, 0, 0], "0.3": [-135, 0, 35], "0.4": [-30, 0, -45], "0.55": [-15, 0, -30], "0.9": [0, 0, 0]}},
        "forearm_right": {"rotation": {"0.0": [0, 0, 0], "0.3": [-35, 0, 0], "0.4": [-5, 0, 0], "0.9": [0, 0, 0]}},
        "hand_right": {"rotation": {"0.0": [0, 0, 0], "0.3": [-20, 0, 0], "0.4": [25, 0, 0], "0.9": [0, 0, 0]}},
        "arm_left": {"rotation": {"0.0": [0, 0, 0], "0.3": [-20, 0, -15], "0.4": [10, 0, -10], "0.9": [0, 0, 0]}},
        "leg_left": {"rotation": {"0.0": [0, 0, 0], "0.3": [-12, 0, 0], "0.5": [-12, 0, 0], "0.9": [0, 0, 0]}},
        "leg_right": {"rotation": {"0.0": [0, 0, 0], "0.3": [10, 0, 0], "0.5": [10, 0, 0], "0.9": [0, 0, 0]}},
    }}

    scream = {"animation_length": 1.6, "bones": {
        "torso": {"rotation": {"0.0": [0, 0, 0], "0.9": [-14, 0, 0], "1.0": [16, 0, 0], "1.3": [12, 0, 0], "1.6": [0, 0, 0]}},
        "head": {"rotation": {"0.0": [0, 0, 0], "0.5": [-18, 0, 4], "0.9": [-26, 0, -4], "1.0": [22, 0, 0], "1.3": [18, 0, 0], "1.6": [0, 0, 0]}},
        "neck": {"rotation": {"0.0": [0, 0, 0], "0.9": [-10, 0, 0], "1.0": [10, 0, 0], "1.6": [0, 0, 0]}},
        "arm_left": {"rotation": {"0.0": [0, 0, 0], "0.9": [-30, 0, -40], "1.0": [-70, 0, -75], "1.3": [-60, 0, -70], "1.6": [0, 0, 0]}},
        "arm_right": {"rotation": {"0.0": [0, 0, 0], "0.9": [-30, 0, 40], "1.0": [-70, 0, 75], "1.3": [-60, 0, 70], "1.6": [0, 0, 0]}},
        "forearm_left": {"rotation": {"0.0": [0, 0, 0], "0.9": [-40, 0, 0], "1.0": [0, 0, 0], "1.6": [0, 0, 0]}},
        "forearm_right": {"rotation": {"0.0": [0, 0, 0], "0.9": [-40, 0, 0], "1.0": [0, 0, 0], "1.6": [0, 0, 0]}},
        "root": {"position": {"0.0": [0, 0, 0], "0.9": [0, -1.5, 1], "1.0": [0, 0, -1], "1.6": [0, 0, 0]}},
    }}
    for b in BELLS:
        scream["bones"][b] = {
            "scale": {"0.0": [1, 1, 1], "0.3": [1.1, 1.1, 1.1], "0.5": [1.05, 1.05, 1.05], "0.7": [1.2, 1.2, 1.2],
                      "0.9": [1.3, 1.3, 1.3], "1.0": [1.65, 1.65, 1.65], "1.2": [1.2, 1.2, 1.2], "1.6": [1, 1, 1]},
            "rotation": {"0.0": [0, 0, 0], "0.6": [0, 0, 6], "0.7": [0, 0, -6], "0.8": [0, 0, 8], "0.9": [0, 0, -8],
                         "1.0": [0, 0, 0], "1.6": [0, 0, 0]},
        }

    jump_out = {"animation_length": 0.5, "loop": "hold_on_last_frame", "bones": {
        "root": {"position": {"0.0": [0, 0, 0], "0.2": [0, -4, 0], "0.5": [0, -10, 0]},
                 "scale": {"0.0": [1, 1, 1], "0.2": [1.05, 0.9, 1.05], "0.5": [0.05, 0.05, 0.05]}},
        "torso": {"rotation": {"0.0": [0, 0, 0], "0.2": [28, 0, 0], "0.5": [40, 0, 0]}},
        "leg_left": {"rotation": {"0.0": [0, 0, 0], "0.2": [-35, 0, 0], "0.5": [-35, 0, 0]}},
        "leg_right": {"rotation": {"0.0": [0, 0, 0], "0.2": [-35, 0, 0], "0.5": [-35, 0, 0]}},
        "shin_left": {"rotation": {"0.0": [0, 0, 0], "0.2": [50, 0, 0], "0.5": [50, 0, 0]}},
        "shin_right": {"rotation": {"0.0": [0, 0, 0], "0.2": [50, 0, 0], "0.5": [50, 0, 0]}},
    }}

    jump_in = {"animation_length": 0.5, "bones": {
        "root": {"position": {"0.0": [0, -10, 0], "0.3": [0, -2, 0], "0.5": [0, 0, 0]},
                 "scale": {"0.0": [0.05, 0.05, 0.05], "0.3": [1.08, 1.12, 1.08], "0.5": [1, 1, 1]}},
        "torso": {"rotation": {"0.0": [30, 0, 0], "0.3": [-8, 0, 0], "0.5": [0, 0, 0]}},
        "arm_left": {"rotation": {"0.0": [0, 0, -50], "0.3": [-20, 0, -30], "0.5": [0, 0, 0]}},
        "arm_right": {"rotation": {"0.0": [0, 0, 50], "0.3": [-20, 0, 30], "0.5": [0, 0, 0]}},
    }}

    silence = {"animation_length": 3.0, "bones": {
        "torso": {"rotation": {"0.0": [0, 0, 0], "0.4": [8, 0, 0], "2.6": [8, 0, 0], "3.0": [0, 0, 0]}},
        "head": {"rotation": {"0.0": [0, 0, 0], "0.4": [10, 0, 22], "1.5": [12, 6, 24], "2.6": [10, -4, 20], "3.0": [0, 0, 0]}},
        "arm_left": {"rotation": {"0.0": [0, 0, 0], "0.4": [-25, 0, -12], "2.6": [-25, 0, -12], "3.0": [0, 0, 0]}},
        "arm_right": {"rotation": {"0.0": [0, 0, 0], "0.4": [-25, 0, 12], "2.6": [-25, 0, 12], "3.0": [0, 0, 0]}},
        "forearm_left": {"rotation": {"0.0": [0, 0, 0], "0.4": [-30, 0, 0], "2.6": [-30, 0, 0], "3.0": [0, 0, 0]}},
        "forearm_right": {"rotation": {"0.0": [0, 0, 0], "0.4": [-30, 0, 0], "2.6": [-30, 0, 0], "3.0": [0, 0, 0]}},
    }}
    for i, b in enumerate(BELLS):
        t0 = 0.4 + i * 0.15
        silence["bones"][b] = {"scale": {"0.0": [1, 1, 1], f"{t0:.2f}": [1.15, 1.15, 1.15],
                                         f"{t0 + 0.6:.2f}": [1.0, 1.0, 1.0], f"{t0 + 1.2:.2f}": [1.15, 1.15, 1.15],
                                         "3.0": [1, 1, 1]}}

    death = {"animation_length": 1.5, "loop": "hold_on_last_frame", "bones": {
        "root": {"position": {"0.0": [0, 0, 0], "1.0": [0, -6, 0], "1.5": [0, -12, 0]},
                 "scale": {"0.0": [1, 1, 1], "0.6": [1.05, 1.05, 1.05], "1.5": [0.05, 0.05, 0.05]}},
        "torso": {"rotation": {"0.0": [0, 0, 0], "0.6": [-20, 0, 0], "1.5": [35, 0, 0]}},
        "head": {"rotation": {"0.0": [0, 0, 0], "0.6": [-30, 0, 0], "1.5": [30, 0, 15]}},
    }}
    for b in BELLS:
        death["bones"][b] = {"scale": {"0.0": [1, 1, 1], "0.6": [1.5, 1.5, 1.5], "1.5": [0.2, 0.2, 0.2]}}

    anims["animation.listener.claw"] = claw
    anims["animation.listener.scream"] = scream
    anims["animation.listener.jump_out"] = jump_out
    anims["animation.listener.jump_in"] = jump_in
    anims["animation.listener.silence"] = silence
    anims["animation.listener.death"] = death

    with open(PATH, "w") as f:
        json.dump(data, f, indent=2)
    print(sorted(anims))


if __name__ == "__main__":
    main()
