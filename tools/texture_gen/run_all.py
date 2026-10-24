"""Regenerate every Dead Air 3 texture/model asset and the review sheets.

Run from anywhere:  python tools/texture_gen/run_all.py
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import gen_blocks  # noqa: E402
import gen_icons  # noqa: E402
import gen_sheets  # noqa: E402
import gen_signal_anchor  # noqa: E402
import gen_skins  # noqa: E402
import gen_walkie_t3  # noqa: E402

if __name__ == "__main__":
    gen_walkie_t3.main()
    gen_icons.main()
    gen_blocks.main()
    gen_skins.main()
    gen_signal_anchor.main()
    gen_sheets.main()
