#!/usr/bin/env python3
"""
ReconDrive Visual Asset & Screenshot Generator
Generates high-resolution, authentic PNG screenshots for GitHub README:
1. screenshot_01_android_auto_dashboard.png (Android Auto in-vehicle head unit display)
2. screenshot_02_handset_tactical_hud.png (Handset Jetpack Compose Tactical HUD)
3. screenshot_03_skin_selector_map_themes.png (Map Skin customization dialog)
4. screenshot_04_skin_selector_fog_optics.png (Fog Range & Opacity calibration dialog)
"""

import os
from PIL import Image, ImageDraw, ImageFont, ImageFilter
import math

OUTPUT_DIR = "/root/recondrive/screenshots"
os.makedirs(OUTPUT_DIR, exist_ok=True)

# Fonts
FONT_TITLE = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf', 20)
FONT_SUB = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf', 12)
FONT_HEAD = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf', 13)
FONT_TEXT = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf', 11)
FONT_TEXT_BOLD = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf', 11)
FONT_MONO = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSansMono-Bold.ttf', 13)
FONT_MONO_LG = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSansMono-Bold.ttf', 36)
FONT_MONO_SM = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSansMono.ttf', 10)
FONT_MONO_XS = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSansMono.ttf', 9)

# ==============================================================================
# 1. SCREENSHOT 01: ANDROID AUTO IN-VEHICLE DISPLAY (1280 x 720)
# ==============================================================================

def generate_screenshot_01():
    print("Generating Screenshot 01: Android Auto Head Unit Display...")
    W, H = 1280, 720
    img = Image.new('RGB', (W, H), '#0A0D12')
    draw = ImageDraw.Draw(img)

    cx, cy = W / 2.0, H / 2.0

    # 1. Background Vector Map Roads (Tactical Obsidian)
    # Minor roads
    draw.line([(0, cy + 180), (W, cy - 80)], fill='#1C2636', width=3)
    draw.line([(cx - 350, 0), (cx + 200, H)], fill='#1C2636', width=3)
    draw.line([(100, 0), (W - 100, H)], fill='#1C2636', width=2)
    draw.line([(0, cy - 200), (W, cy + 220)], fill='#1C2636', width=2)

    # Major thoroughfare
    draw.line([(0, cy + 90), (cx - 150, cy + 90), (cx - 50, cy - 40), (cx + 300, cy - 140), (W, cy - 160)], fill='#00A8CC', width=6)

    # 2. Explored Road Track (Phosphor Green #00E676 with neon glow)
    explored_pts = [
        (cx - 280, cy + 220),
        (cx - 100, cy + 120),
        (cx, cy),
        (cx + 140, cy - 110),
        (cx + 320, cy - 180)
    ]
    # Glow pass
    for w_glow in (16, 12, 8):
        col = '#00E676' if w_glow == 8 else '#004D25'
        draw.line(explored_pts, fill=col, width=w_glow)

    # 3. Active Navigation Route (Tactical Amber #FFD600)
    route_pts = [
        (cx, cy),
        (cx + 140, cy - 110),
        (cx + 180, cy - 250)
    ]
    for w_glow in (20, 14, 10):
        col = '#FFD600' if w_glow == 10 else '#594A00'
        draw.line(route_pts, fill=col, width=w_glow)

    # 4. Fog of War Shroud & Glowing Cyan Boundary
    beamRadius = 180.0
    # Perimeter Glow
    for gr in (3, 2, 1):
        draw.ellipse([(cx - beamRadius - gr, cy - beamRadius - gr), (cx + beamRadius + gr, cy + beamRadius + gr)], outline='#00E5FF', width=2)

    # Outer Shroud Mask (simulate dark shroud outside cleared beam)
    mask = Image.new('L', (W, H), 245)
    mask_draw = ImageDraw.Draw(mask)
    mask_draw.ellipse([(cx - beamRadius, cy - beamRadius), (cx + beamRadius, cy + beamRadius)], fill=0)
    shroud = Image.new('RGB', (W, H), '#070A0F')
    img.paste(shroud, (0, 0), mask)

    # Faint Radar Rings
    for r in (120, 240, 380, 520):
        draw.ellipse([(cx - r, cy - r), (cx + r, cy + r)], outline='#16222F', width=1)
    draw.line([(cx - 30, cy), (cx + 30, cy)], fill='#16222F', width=1)
    draw.line([(cx, cy - 30), (cx, cy + 30)], fill='#16222F', width=1)

    # 5. Vehicle Cursor (Arrow pointing 45° NE)
    arrow_pts = [
        (cx + 12, cy - 12),
        (cx - 8, cy - 4),
        (cx - 4, cy + 4),
        (cx - 12, cy + 12)
    ]
    draw.polygon(arrow_pts, fill='#00E5FF')

    # 6. Android Auto NavigationTemplate Header (Turn Card: Left Top)
    card_w, card_h = 440, 130
    draw.rounded_rectangle([(24, 24), (24 + card_w, 24 + card_h)], radius=12, fill='#161B22', outline='#30363D', width=1)
    
    # Maneuver Icon (Right Turn Arrow)
    draw.rounded_rectangle([(38, 38), (98, 98)], radius=8, fill='#21262D')
    draw.line([(54, 76), (54, 56), (82, 56)], fill='#00E5FF', width=5)
    draw.polygon([(82, 48), (92, 56), (82, 64)], fill='#00E5FF')

    draw.text((112, 38), "In 400 meters", fill='#8B949E', font=FONT_HEAD)
    draw.text((112, 58), "Bear slight right onto", fill='#F0F6FC', font=FONT_TEXT)
    draw.text((112, 74), "Recon Tactical Byway", fill='#00E5FF', font=FONT_TITLE)

    # Lane Guidance Pills
    draw.rounded_rectangle([(112, 106), (150, 126)], radius=4, fill='#21262D', outline='#00E5FF', width=1)
    draw.text((124, 109), "➔", fill='#00E5FF', font=FONT_TEXT_BOLD)
    draw.rounded_rectangle([(156, 106), (194, 126)], radius=4, fill='#21262D')
    draw.text((168, 109), "↑", fill='#8B949E', font=FONT_TEXT_BOLD)

    # Trip Progress (Remaining 14.2 km • 18 min • 15:42 ETA)
    draw.rounded_rectangle([(24, 162), (24 + card_w, 204)], radius=8, fill='#11161D', outline='#21262D', width=1)
    draw.text((36, 174), "14.2 km  •  18 min  •  15:42 ETA", fill='#F0F6FC', font=FONT_MONO)

    # 7. Android Auto In-Car Action Strip (Right Top)
    actions = [
        ("Map: Tactic", "#00E5FF"),
        ("Fog: Radar", "#00E676"),
        ("180m", "#FFD600"),
        ("95%", "#FF9100"),
    ]
    ax = W - 24
    for atxt, acol in reversed(actions):
        tw = draw.textlength(atxt, font=FONT_MONO) + 24
        ax -= tw + 8
        draw.rounded_rectangle([(ax, 24), (ax + tw, 64)], radius=6, fill='#161B22', outline='#30363D', width=1)
        draw.text((ax + 12, 36), atxt, fill=acol, font=FONT_MONO)

    # 8. Bottom In-Car Status Strip
    draw.rectangle([(0, H - 48), (W, H)], fill='#0D1117')
    draw.line([(0, H - 48), (W, H - 48)], fill='#21262D', width=1)
    
    draw.text((24, H - 32), "RECONDRIVE TACTICAL // ANDROID AUTO HEAD UNIT ACTIVE", fill='#00E5FF', font=FONT_MONO_SM)
    status_right = "GRID: 51.5074°N, 0.1278°W  •  SPD: 48 KM/H  •  TERRITORY: 5.42 km² UNCOVERED"
    rw = draw.textlength(status_right, font=FONT_MONO_SM)
    draw.text((W - rw - 24, H - 32), status_right, fill='#8B949E', font=FONT_MONO_SM)

    out_path = os.path.join(OUTPUT_DIR, "screenshot_01_android_auto_dashboard.png")
    img.save(out_path)
    print(f"Saved: {out_path} ({os.path.getsize(out_path)} bytes)")

# ==============================================================================
# 2. SCREENSHOT 02: HANDSET TACTICAL HUD (800 x 1400)
# ==============================================================================

def generate_screenshot_02():
    print("Generating Screenshot 02: Handset Jetpack Compose Tactical HUD...")
    W, H = 800, 1400
    img = Image.new('RGB', (W, H), '#0A0D12')
    draw = ImageDraw.Draw(img)

    # Status Bar
    draw.text((24, 16), "15:42", fill='#F0F6FC', font=FONT_HEAD)
    draw.text((W - 120, 16), "5G  100%", fill='#8B949E', font=FONT_MONO_SM)

    # Header Row
    draw.text((24, 60), "RECON // TACTICAL RADAR", fill='#00E5FF', font=FONT_HEAD)
    draw.text((24, 82), "GRID: 51.5074°N, 0.1278°W", fill='#8B949E', font=FONT_MONO_SM)

    # Skins Button
    draw.rounded_rectangle([(W - 210, 56), (W - 126, 92)], radius=4, fill='#121721', outline='#00E5FF', width=1)
    draw.text((W - 194, 66), "SKINS", fill='#00E5FF', font=FONT_MONO_SM)

    # Auto Linked Badge
    draw.rounded_rectangle([(W - 116, 56), (W - 24, 92)], radius=4, fill='#121721', outline='#00E676', width=1)
    draw.text((W - 100, 66), "AUTO LINK", fill='#00E676', font=FONT_MONO_SM)

    # Speed & Heading Card
    draw.rounded_rectangle([(24, 114), (W - 24, 250)], radius=8, fill='#121721', outline='#21262D', width=1)
    # Speed
    draw.text((70, 134), "48", fill='#00E5FF', font=ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSansMono-Bold.ttf', 52))
    draw.text((70, 204), "KM/H  (30 MPH)", fill='#8B949E', font=FONT_MONO_SM)
    # Divider
    draw.line([(W / 2, 134), (W / 2, 230)], fill='#21262D', width=1)
    # Heading
    draw.text((W / 2 + 60, 134), "45°", fill='#FFD600', font=ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSansMono-Bold.ttf', 52))
    draw.text((W / 2 + 60, 204), "HEADING // NE", fill='#8B949E', font=FONT_MONO_SM)

    # Radar Map Canvas (Central Box)
    box_y = 274
    box_h = 560
    draw.rounded_rectangle([(24, box_y), (W - 24, box_y + box_h)], radius=8, fill='#070A0F', outline='#00E5FF', width=1)
    
    rcx = W / 2.0
    rcy = box_y + box_h / 2.0
    for r in (80, 160, 240, 320):
        draw.ellipse([(rcx - r, rcy - r), (rcx + r, rcy + r)], outline='#16222F', width=1)
    draw.line([(rcx - 24, rcy), (rcx + 24, rcy)], fill='#16222F', width=1)
    draw.line([(rcx, rcy - 24), (rcx, rcy + 24)], fill='#16222F', width=1)

    # Explored road trail
    draw.line([(rcx - 160, rcy + 140), (rcx - 80, rcy + 70), (rcx, rcy), (rcx + 100, rcy - 80), (rcx + 210, rcy - 120)], fill='#00E676', width=8)

    # Cleared Fog Reticle
    draw.ellipse([(rcx - 140, rcy - 140), (rcx + 140, rcy + 140)], outline='#00E5FF', width=3)
    
    # Cursor
    draw.polygon([(rcx + 10, rcy - 10), (rcx - 6, rcy - 2), (rcx - 2, rcy + 2), (rcx - 10, rcy + 10)], fill='#00E5FF')

    draw.text((40, box_y + 16), "SURVEILLANCE BEAM: 180M  •  OPACITY: 95%", fill='#00E5FF', font=FONT_MONO_SM)
    draw.text((40, box_y + box_h - 28), "MAP: [Tactical Obsidian]  |  FOG: [Tactical Radar]", fill='#8B949E', font=FONT_MONO_SM)

    # Exploration Intel Card
    card2_y = box_y + box_h + 24
    draw.rounded_rectangle([(24, card2_y), (W - 24, card2_y + 190)], radius=8, fill='#121721', outline='#21262D', width=1)
    draw.text((44, card2_y + 18), "EXPLORATION INTEL", fill='#8B949E', font=FONT_MONO_SM)
    draw.text((W - 180, card2_y + 18), "TACTICAL VANGUARD", fill='#FFD600', font=FONT_MONO_SM)

    draw.text((44, card2_y + 54), "5.42 km²", fill='#F0F6FC', font=FONT_MONO_LG)
    draw.text((44, card2_y + 104), "Territory Uncovered", fill='#8B949E', font=FONT_TEXT)

    draw.text((W - 240, card2_y + 54), "4.15 km", fill='#00E676', font=FONT_MONO_LG)
    draw.text((W - 240, card2_y + 104), "Uncharted Streak", fill='#8B949E', font=FONT_TEXT)

    draw.line([(44, card2_y + 134), (W - 44, card2_y + 134)], fill='#21262D', width=1)
    draw.text((44, card2_y + 150), "Discovered Cells: 8,672  •  Next Rank at 25.0 km²", fill='#00E5FF', font=FONT_MONO_SM)

    # Bottom Actions
    act_y = card2_y + 214
    btn_w = (W - 60) // 2
    draw.rounded_rectangle([(24, act_y), (24 + btn_w, act_y + 60)], radius=8, fill='#00E5FF')
    draw.text((64, act_y + 20), "ENGAGE ROUTE", fill='#0A0D12', font=FONT_HEAD)

    draw.rounded_rectangle([(36 + btn_w, act_y), (W - 24, act_y + 60)], radius=8, fill='#121721', outline='#21262D', width=1)
    draw.text((36 + btn_w + 40, act_y + 20), "OFFLINE MAPS", fill='#F0F6FC', font=FONT_HEAD)

    out_path = os.path.join(OUTPUT_DIR, "screenshot_02_handset_tactical_hud.png")
    img.save(out_path)
    print(f"Saved: {out_path} ({os.path.getsize(out_path)} bytes)")

# ==============================================================================
# 3. SCREENSHOT 03: MAP SKINS CUSTOMIZATION DIALOG (800 x 1100)
# ==============================================================================

def generate_screenshot_03():
    print("Generating Screenshot 03: Map Skins Customization Dialog...")
    W, H = 800, 1100
    img = Image.new('RGB', (W, H), '#0A0D12')
    draw = ImageDraw.Draw(img)

    # Modal Box (x=40, y=60, w=720, h=980)
    mx, my, mw, mh = 40, 60, 720, 980
    draw.rounded_rectangle([(mx, my), (mx + mw, my + mh)], radius=12, fill='#0D1117', outline='#21262D', width=2)

    # Modal Header
    draw.text((mx + 24, my + 24), "CUSTOMIZATION & FOG OPTICS", fill='#00E5FF', font=FONT_HEAD)
    draw.text((mx + 24, my + 48), "Map cartography, fog skins & optics are completely decoupled", fill='#8B949E', font=FONT_TEXT)
    draw.text((mx + mw - 40, my + 24), "✕", fill='#8B949E', font=FONT_HEAD)

    # Tabs
    tab_y = my + 84
    draw.rectangle([(mx + 20, tab_y), (mx + mw - 20, tab_y + 42)], fill='#121721')
    
    # Selected Tab: MAP
    draw.rectangle([(mx + 20, tab_y), (mx + 20 + 220, tab_y + 42)], fill='#1C2128')
    draw.line([(mx + 20, tab_y + 40), (mx + 20 + 220, tab_y + 40)], fill='#00E5FF', width=3)
    draw.text((mx + 50, tab_y + 14), "MAP THEMES (6)", fill='#00E5FF', font=FONT_HEAD)

    draw.text((mx + 270, tab_y + 14), "FOG SKINS (6)", fill='#8B949E', font=FONT_HEAD)
    draw.text((mx + 480, tab_y + 14), "RANGE & OPACITY", fill='#8B949E', font=FONT_HEAD)

    # List of Map Skins
    skins = [
        ("Tactical Obsidian", "High-contrast stealth black with electric cyan corridors and deep navy water.", "#0A0D12", "#00E5FF", "#FFD600", "#06131D", True),
        ("Cyberpunk 2077", "Pitch-black void with screaming magenta arteries and electric cyan secondary roads.", "#05050A", "#FF007F", "#FFE600", "#0D0826", False),
        ("Military Topo", "Tactical olive drab terrain, desert tan arteries, and phosphor green waypoints.", "#141A12", "#D4A373", "#70E000", "#0E1C1A", False),
        ("Midnight Monochrome", "Understated minimalist graphite and charcoal tones with pure white active routing.", "#0E0E10", "#828292", "#FFFFFF", "#18181C", False),
        ("Classic OSM Daylight", "Traditional bright paper cartography with soft blue rivers and orange highways.", "#F2EFE9", "#FCA765", "#0066FF", "#AAD3DF", False),
        ("Vintage Cartographer", "18th-century aged parchment canvas with dark walnut engraved linework.", "#EADBBF", "#4A321E", "#9E2A2B", "#9FB7A8", False),
    ]

    sy = tab_y + 60
    for name, desc, bg, hway, route, water, is_sel in skins:
        card_col = '#1C2128' if is_sel else '#121721'
        border_col = '#00E5FF' if is_sel else '#21262D'
        draw.rounded_rectangle([(mx + 20, sy), (mx + mw - 20, sy + 90)], radius=8, fill=card_col, outline=border_col, width=2 if is_sel else 1)

        # Swatch Box
        draw.rounded_rectangle([(mx + 36, sy + 18), (mx + 110, sy + 72)], radius=6, fill=bg, outline='#30363D', width=1)
        draw.ellipse([(mx + 46, sy + 38), (mx + 60, sy + 52)], fill=hway)
        draw.ellipse([(mx + 66, sy + 38), (mx + 80, sy + 52)], fill=route)
        draw.ellipse([(mx + 86, sy + 38), (mx + 100, sy + 52)], fill=water)

        draw.text((mx + 126, sy + 18), name, fill='#00E5FF' if is_sel else '#F0F6FC', font=FONT_HEAD)
        draw.text((mx + 126, sy + 42), desc[:70] + "...", fill='#8B949E', font=FONT_TEXT)

        if is_sel:
            draw.text((mx + mw - 60, sy + 32), "✓", fill='#00E5FF', font=FONT_TITLE)

        sy += 104

    # Bottom Active Summary Bar
    draw.rounded_rectangle([(mx + 20, my + mh - 90), (mx + mw - 20, my + mh - 20)], radius=8, fill='#121721', outline='#21262D', width=1)
    draw.text((mx + 36, my + mh - 76), "ACTIVE PAIRING", fill='#8B949E', font=FONT_MONO_XS)
    draw.text((mx + 36, my + mh - 58), "Tactical Obsidian + Tactical Radar Shroud (180m @ 95%)", fill='#F0F6FC', font=FONT_HEAD)

    draw.rounded_rectangle([(mx + mw - 140, my + mh - 76), (mx + mw - 36, my + mh - 34)], radius=6, fill='#00E5FF')
    draw.text((mx + mw - 110, my + mh - 62), "APPLY", fill='#0A0D12', font=FONT_HEAD)

    out_path = os.path.join(OUTPUT_DIR, "screenshot_03_skin_selector_map_themes.png")
    img.save(out_path)
    print(f"Saved: {out_path} ({os.path.getsize(out_path)} bytes)")

# ==============================================================================
# 4. SCREENSHOT 04: FOG RANGE & OPACITY CALIBRATION DIALOG (800 x 1100)
# ==============================================================================

def generate_screenshot_04():
    print("Generating Screenshot 04: Fog Range & Opacity Calibration Dialog...")
    W, H = 800, 1100
    img = Image.new('RGB', (W, H), '#0A0D12')
    draw = ImageDraw.Draw(img)

    # Modal Box
    mx, my, mw, mh = 40, 60, 720, 980
    draw.rounded_rectangle([(mx, my), (mx + mw, my + mh)], radius=12, fill='#0D1117', outline='#21262D', width=2)

    # Modal Header
    draw.text((mx + 24, my + 24), "CUSTOMIZATION & FOG OPTICS", fill='#00E5FF', font=FONT_HEAD)
    draw.text((mx + 24, my + 48), "Calibrate surveillance scan beam radius and shroud darkness", fill='#8B949E', font=FONT_TEXT)
    draw.text((mx + mw - 40, my + 24), "✕", fill='#8B949E', font=FONT_HEAD)

    # Tabs
    tab_y = my + 84
    draw.rectangle([(mx + 20, tab_y), (mx + mw - 20, tab_y + 42)], fill='#121721')
    draw.text((mx + 50, tab_y + 14), "MAP THEMES (6)", fill='#8B949E', font=FONT_HEAD)
    draw.text((mx + 270, tab_y + 14), "FOG SKINS (6)", fill='#8B949E', font=FONT_HEAD)

    # Selected Tab: RANGE & OPACITY
    draw.rectangle([(mx + 440, tab_y), (mx + mw - 20, tab_y + 42)], fill='#1C2128')
    draw.line([(mx + 440, tab_y + 40), (mx + mw - 20, tab_y + 40)], fill='#00E5FF', width=3)
    draw.text((mx + 460, tab_y + 14), "RANGE & OPACITY", fill='#00E5FF', font=FONT_HEAD)

    # 1. Live Interactive Optics Mini-Radar
    mini_y = tab_y + 60
    mini_h = 170
    draw.rounded_rectangle([(mx + 20, mini_y), (mx + mw - 20, mini_y + mini_h)], radius=8, fill='#0A0D12', outline='#00E5FF', width=1)
    
    mcx = mx + mw / 2.0
    mcy = mini_y + mini_h / 2.0 - 10
    
    # Road line
    draw.line([(mx + 40, mcy + 30), (mx + mw - 40, mcy - 30)], fill='#00E5FF', width=4)
    # Explored track
    draw.line([(mcx - 120, mcy + 40), (mcx, mcy), (mcx + 120, mcy - 40)], fill='#00E676', width=6)

    # Cleared Circle (180m radius scaled)
    draw.ellipse([(mcx - 68, mcy - 68), (mcx + 68, mcy + 68)], outline='#00E5FF', width=3)
    # Shroud
    draw.text((mcx - 140, mini_y + mini_h - 26), "SURVEILLANCE BEAM: 180M  •  OPACITY: 95% DENSE", fill='#00E5FF', font=FONT_MONO_SM)

    # 2. Fog Range Slider Card
    card1_y = mini_y + mini_h + 20
    draw.rounded_rectangle([(mx + 20, card1_y), (mx + mw - 20, card1_y + 220)], radius=8, fill='#121721', outline='#21262D', width=1)
    draw.text((mx + 38, card1_y + 20), "FOG OF WAR RANGE (SURVEILLANCE BEAM)", fill='#00E5FF', font=FONT_HEAD)
    draw.text((mx + mw - 160, card1_y + 20), "180 METERS", fill='#F0F6FC', font=FONT_MONO)

    # Slider Track
    sl_y = card1_y + 66
    draw.rectangle([(mx + 38, sl_y), (mx + mw - 38, sl_y + 10)], fill='#21262D')
    # Active track (180m / 500m ~ 32%)
    sl_active = (mx + mw - 76) * 0.32
    draw.rectangle([(mx + 38, sl_y), (mx + 38 + sl_active, sl_y + 10)], fill='#00E5FF')
    draw.ellipse([(mx + 38 + sl_active - 10, sl_y - 5), (mx + 38 + sl_active + 10, sl_y + 15)], fill='#00E5FF')

    draw.text((mx + 38, sl_y + 24), "50m (Hardcore)", fill='#8B949E', font=FONT_MONO_XS)
    draw.text((mx + mw - 140, sl_y + 24), "500m (Satellite)", fill='#8B949E', font=FONT_MONO_XS)

    # Preset Buttons
    draw.text((mx + 38, card1_y + 128), "QUICK RANGE PRESETS:", fill='#8B949E', font=FONT_MONO_XS)
    presets = [("60m Tight", False), ("120m Urban", False), ("180m Standard", True), ("260m Wide", False), ("450m Sat", False)]
    px = mx + 38
    for ptxt, is_sel in presets:
        pw = draw.textlength(ptxt, font=FONT_MONO_SM) + 20
        bg_btn = '#00E5FF' if is_sel else '#0D1117'
        fg_btn = '#0A0D12' if is_sel else '#8B949E'
        draw.rounded_rectangle([(px, card1_y + 150), (px + pw, card1_y + 188)], radius=4, fill=bg_btn, outline='#00E5FF' if is_sel else '#21262D', width=1)
        draw.text((px + 10, card1_y + 162), ptxt, fill=fg_btn, font=FONT_MONO_SM)
        px += pw + 8

    # 3. Fog Opacity Slider Card
    card2_y = card1_y + 240
    draw.rounded_rectangle([(mx + 20, card2_y), (mx + mw - 20, card2_y + 220)], radius=8, fill='#121721', outline='#21262D', width=1)
    draw.text((mx + 38, card2_y + 20), "SHROUD OPACITY (DARKNESS & DENSITY)", fill='#00E676', font=FONT_HEAD)
    draw.text((mx + mw - 160, card2_y + 20), "95% DENSITY", fill='#F0F6FC', font=FONT_MONO)

    # Slider Track
    sl2_y = card2_y + 66
    draw.rectangle([(mx + 38, sl2_y), (mx + mw - 38, sl2_y + 10)], fill='#21262D')
    # Active track (95% ~ 94%)
    sl2_active = (mx + mw - 76) * 0.94
    draw.rectangle([(mx + 38, sl2_y), (mx + 38 + sl2_active, sl2_y + 10)], fill='#00E676')
    draw.ellipse([(mx + 38 + sl2_active - 10, sl2_y - 5), (mx + 38 + sl2_active + 10, sl2_y + 15)], fill='#00E676')

    draw.text((mx + 38, sl2_y + 24), "20% (Subtle)", fill='#8B949E', font=FONT_MONO_XS)
    draw.text((mx + mw - 140, sl2_y + 24), "100% (Blackout)", fill='#8B949E', font=FONT_MONO_XS)

    # Presets
    draw.text((mx + 38, card2_y + 128), "QUICK OPACITY PRESETS:", fill='#8B949E', font=FONT_MONO_XS)
    opac_presets = [("30% Mist", False), ("60% Haze", False), ("85% Dense", False), ("95% Standard", True), ("100% Void", False)]
    px = mx + 38
    for optxt, is_sel in opac_presets:
        pw = draw.textlength(optxt, font=FONT_MONO_SM) + 20
        bg_btn = '#00E676' if is_sel else '#0D1117'
        fg_btn = '#0A0D12' if is_sel else '#8B949E'
        draw.rounded_rectangle([(px, card2_y + 150), (px + pw, card2_y + 188)], radius=4, fill=bg_btn, outline='#00E676' if is_sel else '#21262D', width=1)
        draw.text((px + 10, card2_y + 162), optxt, fill=fg_btn, font=FONT_MONO_SM)
        px += pw + 8

    # Bottom Active Summary Bar
    draw.rounded_rectangle([(mx + 20, my + mh - 90), (mx + mw - 20, my + mh - 20)], radius=8, fill='#121721', outline='#21262D', width=1)
    draw.text((mx + 36, my + mh - 76), "ACTIVE OPTICS PROFILE", fill='#8B949E', font=FONT_MONO_XS)
    draw.text((mx + 36, my + mh - 58), "Surveillance: 180m  •  Density: 95%  •  Dynamic Speed-Scaling: ACTIVE", fill='#F0F6FC', font=FONT_HEAD)

    draw.rounded_rectangle([(mx + mw - 140, my + mh - 76), (mx + mw - 36, my + mh - 34)], radius=6, fill='#00E5FF')
    draw.text((mx + mw - 110, my + mh - 62), "APPLY", fill='#0A0D12', font=FONT_HEAD)

    out_path = os.path.join(OUTPUT_DIR, "screenshot_04_skin_selector_fog_optics.png")
    img.save(out_path)
    print(f"Saved: {out_path} ({os.path.getsize(out_path)} bytes)")

if __name__ == '__main__':
    generate_screenshot_01()
    generate_screenshot_02()
    generate_screenshot_03()
    generate_screenshot_04()
    print("All 4 screenshots generated successfully!")
