#!/usr/bin/env python3
"""
ReconDrive Visual Asset & Screenshot Generator
Generates high-resolution, pixel-perfect PNG screenshots for GitHub README:
1. screenshot_01_android_auto_dashboard.png (Android Auto in-vehicle head unit display, 1280x720)
2. screenshot_02_handset_tactical_hud.png (Handset Jetpack Compose Tactical HUD, 800x1400)
3. screenshot_03_skin_selector_map_themes.png (Map Skin customization dialog, 800x1100)
4. screenshot_04_skin_selector_fog_optics.png (Fog Range & Opacity calibration dialog, 800x1100)
"""

import os
from PIL import Image, ImageDraw, ImageFont
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
FONT_MONO_XL = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSansMono-Bold.ttf', 52)
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
    for gr in (4, 3, 2, 1):
        draw.ellipse([(cx - beamRadius - gr, cy - beamRadius - gr), (cx + beamRadius + gr, cy + beamRadius + gr)], outline='#00E5FF', width=2)

    # Outer Shroud Mask (simulate dark shroud outside cleared beam)
    mask = Image.new('L', (W, H), 242)
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
        (cx + 14, cy - 14),
        (cx - 8, cy - 4),
        (cx - 4, cy + 4),
        (cx - 14, cy + 14)
    ]
    draw.polygon(arrow_pts, fill='#00E5FF')

    # 6. Android Auto NavigationTemplate Header (Turn Card: Left Top)
    card_w, card_h = 440, 142
    draw.rounded_rectangle([(24, 24), (24 + card_w, 24 + card_h)], radius=12, fill='#161B22', outline='#30363D', width=1)
    
    # Maneuver Icon Box (64 x 64)
    draw.rounded_rectangle([(38, 38), (102, 102)], radius=8, fill='#21262D')
    # Right turn arrow graphic
    draw.line([(56, 80), (56, 60), (84, 60)], fill='#00E5FF', width=5)
    draw.polygon([(84, 52), (94, 60), (84, 68)], fill='#00E5FF')

    draw.text((116, 36), "In 400 meters", fill='#8B949E', font=FONT_HEAD)
    draw.text((116, 56), "Bear slight right onto", fill='#F0F6FC', font=FONT_TEXT)
    draw.text((116, 74), "Recon Tactical Byway", fill='#00E5FF', font=FONT_TITLE)

    # Lane Guidance Pills (Turn Right active, Go Straight secondary)
    draw.rounded_rectangle([(116, 108), (154, 130)], radius=4, fill='#21262D', outline='#00E5FF', width=1)
    draw.text((127, 110), "➔", fill='#00E5FF', font=FONT_TEXT_BOLD)
    draw.rounded_rectangle([(160, 108), (198, 130)], radius=4, fill='#21262D')
    draw.text((172, 110), "↑", fill='#8B949E', font=FONT_TEXT_BOLD)

    # Trip Progress (Remaining 14.2 km • 18 min • 15:42 ETA)
    draw.rounded_rectangle([(24, 178), (24 + card_w, 222)], radius=8, fill='#11161D', outline='#21262D', width=1)
    draw.text((40, 191), "14.2 km  •  18 min  •  15:42 ETA", fill='#F0F6FC', font=FONT_MONO)

    # Speed limit roundel badge (50 km/h)
    draw.ellipse([(24 + card_w - 56, 178 + 6), (24 + card_w - 24, 178 + 38)], outline='#FF3333', width=3, fill='#F0F6FC')
    draw.text((24 + card_w - 49, 178 + 14), "50", fill='#0A0D12', font=FONT_MONO_SM)

    # 7. Android Auto In-Car Action Strip (Right Top)
    actions = [
        ("Map: Tactic", "#00E5FF"),
        ("Fog: Radar", "#00E676"),
        ("180m", "#FFD600"),
        ("95%", "#FF9100"),
    ]
    ax = W - 24
    for atxt, acol in reversed(actions):
        tw = draw.textlength(atxt, font=FONT_MONO) + 28
        ax -= tw + 8
        draw.rounded_rectangle([(ax, 24), (ax + tw, 64)], radius=6, fill='#161B22', outline='#30363D', width=1)
        draw.text((ax + 14, 37), atxt, fill=acol, font=FONT_MONO)

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
    draw.text((W - 192, 68), "SKINS", fill='#00E5FF', font=FONT_MONO_SM)

    # Auto Linked Badge
    draw.rounded_rectangle([(W - 116, 56), (W - 24, 92)], radius=4, fill='#121721', outline='#00E676', width=1)
    draw.text((W - 104, 68), "AUTO LINK", fill='#00E676', font=FONT_MONO_SM)

    # Speed & Heading Card
    draw.rounded_rectangle([(24, 114), (W - 24, 250)], radius=8, fill='#121721', outline='#21262D', width=1)
    
    # Speed (Left Half Centered)
    sp_val = "48"
    sp_w = draw.textlength(sp_val, font=FONT_MONO_XL)
    left_mid = 24 + (W - 48) * 0.25
    draw.text((left_mid - sp_w / 2, 134), sp_val, fill='#00E5FF', font=FONT_MONO_XL)
    lbl_sp = "KM/H  (30 MPH)"
    lbl_sp_w = draw.textlength(lbl_sp, font=FONT_MONO_SM)
    draw.text((left_mid - lbl_sp_w / 2, 204), lbl_sp, fill='#8B949E', font=FONT_MONO_SM)

    # Divider Line
    draw.line([(W / 2, 130), (W / 2, 234)], fill='#21262D', width=1)

    # Heading (Right Half Centered)
    hd_val = "45°"
    hd_w = draw.textlength(hd_val, font=FONT_MONO_XL)
    right_mid = 24 + (W - 48) * 0.75
    draw.text((right_mid - hd_w / 2, 134), hd_val, fill='#FFD600', font=FONT_MONO_XL)
    lbl_hd = "HEADING // NE"
    lbl_hd_w = draw.textlength(lbl_hd, font=FONT_MONO_SM)
    draw.text((right_mid - lbl_hd_w / 2, 204), lbl_hd, fill='#8B949E', font=FONT_MONO_SM)

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
    
    # Vehicle Cursor
    draw.polygon([(rcx + 12, rcy - 12), (rcx - 6, rcy - 2), (rcx - 2, rcy + 2), (rcx - 12, rcy + 12)], fill='#00E5FF')

    draw.text((40, box_y + 16), "SURVEILLANCE BEAM: 180M  •  OPACITY: 95%", fill='#00E5FF', font=FONT_MONO_SM)
    draw.text((40, box_y + box_h - 28), "MAP: [Tactical Obsidian]  |  FOG: [Tactical Radar]", fill='#8B949E', font=FONT_MONO_SM)

    # Exploration Intel Card
    card2_y = box_y + box_h + 24
    card2_h = 190
    draw.rounded_rectangle([(24, card2_y), (W - 24, card2_y + card2_h)], radius=8, fill='#121721', outline='#21262D', width=1)
    draw.text((44, card2_y + 18), "EXPLORATION INTEL", fill='#8B949E', font=FONT_MONO_SM)
    draw.text((W - 180, card2_y + 18), "TACTICAL VANGUARD", fill='#FFD600', font=FONT_MONO_SM)

    draw.text((44, card2_y + 54), "5.42 km²", fill='#F0F6FC', font=FONT_MONO_LG)
    draw.text((44, card2_y + 104), "Territory Uncovered", fill='#8B949E', font=FONT_TEXT)

    draw.text((W - 240, card2_y + 54), "4.15 km", fill='#00E676', font=FONT_MONO_LG)
    draw.text((W - 240, card2_y + 104), "Uncharted Streak", fill='#8B949E', font=FONT_TEXT)

    draw.line([(44, card2_y + 134), (W - 44, card2_y + 134)], fill='#21262D', width=1)
    draw.text((44, card2_y + 150), "Discovered Cells: 8,672  •  Next Rank at 25.0 km²", fill='#00E5FF', font=FONT_MONO_SM)

    # Middle Action Buttons
    act_y = card2_y + card2_h + 20
    btn_w = (W - 60) // 2
    
    # Left Button: ENGAGE ROUTE
    draw.rounded_rectangle([(24, act_y), (24 + btn_w, act_y + 58)], radius=8, fill='#00E5FF')
    txt_engage = "ENGAGE ROUTE"
    te_w = draw.textlength(txt_engage, font=FONT_HEAD)
    draw.text((24 + (btn_w - te_w) / 2, act_y + 22), txt_engage, fill='#0A0D12', font=FONT_HEAD)

    # Right Button: OFFLINE MAPS
    draw.rounded_rectangle([(36 + btn_w, act_y), (W - 24, act_y + 58)], radius=8, fill='#121721', outline='#21262D', width=1)
    txt_maps = "OFFLINE MAPS"
    tm_w = draw.textlength(txt_maps, font=FONT_HEAD)
    draw.text((36 + btn_w + (btn_w - tm_w) / 2, act_y + 22), txt_maps, fill='#F0F6FC', font=FONT_HEAD)

    # Bottom Android Navigation Bar (Tabs: HUD, ROUTE, SKINS, INTEL)
    nav_y = H - 90
    draw.rectangle([(0, nav_y), (W, H)], fill='#0D1117')
    draw.line([(0, nav_y), (W, nav_y)], fill='#21262D', width=1)
    
    tabs = [
        ("HUD", "#00E5FF", True),
        ("ROUTE", "#8B949E", False),
        ("SKINS", "#8B949E", False),
        ("INTEL", "#8B949E", False),
    ]
    tab_step = W / len(tabs)
    for i, (tname, tcol, is_active) in enumerate(tabs):
        tx_c = i * tab_step + tab_step / 2
        tw = draw.textlength(tname, font=FONT_HEAD)
        draw.text((tx_c - tw / 2, nav_y + 16), tname, fill=tcol, font=FONT_HEAD)
        if is_active:
            draw.line([(tx_c - 16, nav_y + 36), (tx_c + 16, nav_y + 36)], fill='#00E5FF', width=2)

    # Android Home Gesture Bar
    draw.rounded_rectangle([(W / 2 - 60, H - 14), (W / 2 + 60, H - 10)], radius=2, fill='#484F58')

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

    # 3 Balanced Tabs (Width = 680 / 3 = 226px each)
    tab_y = my + 84
    tab_w = (mw - 40) // 3
    draw.rectangle([(mx + 20, tab_y), (mx + mw - 20, tab_y + 42)], fill='#121721')
    
    # Selected Tab: MAP THEMES (Tab 0)
    draw.rectangle([(mx + 20, tab_y), (mx + 20 + tab_w, tab_y + 42)], fill='#1C2128')
    draw.line([(mx + 20, tab_y + 40), (mx + 20 + tab_w, tab_y + 40)], fill='#00E5FF', width=3)
    
    t0 = "MAP THEMES (6)"
    draw.text((mx + 20 + (tab_w - draw.textlength(t0, font=FONT_HEAD)) / 2, tab_y + 14), t0, fill='#00E5FF', font=FONT_HEAD)
    
    t1 = "FOG SKINS (6)"
    draw.text((mx + 20 + tab_w + (tab_w - draw.textlength(t1, font=FONT_HEAD)) / 2, tab_y + 14), t1, fill='#8B949E', font=FONT_HEAD)
    
    t2 = "RANGE & OPACITY"
    draw.text((mx + 20 + 2 * tab_w + (tab_w - draw.textlength(t2, font=FONT_HEAD)) / 2, tab_y + 14), t2, fill='#8B949E', font=FONT_HEAD)

    # List of Map Skins (Full clean descriptions)
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
        draw.text((mx + 126, sy + 42), desc, fill='#8B949E', font=FONT_TEXT)

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

    # 3 Balanced Tabs (Width = 680 / 3 = 226px each)
    tab_y = my + 84
    tab_w = (mw - 40) // 3
    draw.rectangle([(mx + 20, tab_y), (mx + mw - 20, tab_y + 42)], fill='#121721')
    
    t0 = "MAP THEMES (6)"
    draw.text((mx + 20 + (tab_w - draw.textlength(t0, font=FONT_HEAD)) / 2, tab_y + 14), t0, fill='#8B949E', font=FONT_HEAD)
    
    t1 = "FOG SKINS (6)"
    draw.text((mx + 20 + tab_w + (tab_w - draw.textlength(t1, font=FONT_HEAD)) / 2, tab_y + 14), t1, fill='#8B949E', font=FONT_HEAD)

    # Selected Tab: RANGE & OPACITY (Tab 2)
    draw.rectangle([(mx + 20 + 2 * tab_w, tab_y), (mx + mw - 20, tab_y + 42)], fill='#1C2128')
    draw.line([(mx + 20 + 2 * tab_w, tab_y + 40), (mx + mw - 20, tab_y + 40)], fill='#00E5FF', width=3)
    t2 = "RANGE & OPACITY"
    draw.text((mx + 20 + 2 * tab_w + (tab_w - draw.textlength(t2, font=FONT_HEAD)) / 2, tab_y + 14), t2, fill='#00E5FF', font=FONT_HEAD)

    # 1. Live Interactive Optics Mini-Radar
    mini_y = tab_y + 60
    mini_h = 170
    draw.rounded_rectangle([(mx + 20, mini_y), (mx + mw - 20, mini_y + mini_h)], radius=8, fill='#0A0D12', outline='#00E5FF', width=1)
    
    mcx = mx + mw / 2.0
    mcy = mini_y + mini_h / 2.0 - 10
    
    # Faint concentric radar range circles
    for rad in (30, 60, 90):
        draw.ellipse([(mcx - rad, mcy - rad), (mcx + rad, mcy + rad)], outline='#16222F', width=1)
    draw.line([(mcx - 100, mcy), (mcx + 100, mcy)], fill='#16222F', width=1)
    draw.line([(mcx, mcy - 70), (mcx, mcy + 70)], fill='#16222F', width=1)

    # Road line
    draw.line([(mx + 40, mcy + 30), (mx + mw - 40, mcy - 30)], fill='#00A8CC', width=4)
    # Explored track
    draw.line([(mcx - 120, mcy + 40), (mcx, mcy), (mcx + 120, mcy - 40)], fill='#00E676', width=6)

    # Cleared Circle (180m radius scaled)
    for gr in (3, 2, 1):
        draw.ellipse([(mcx - 68 - gr, mcy - 68 - gr), (mcx + 68 + gr, mcy + 68 + gr)], outline='#00E5FF', width=1)
    draw.ellipse([(mcx - 68, mcy - 68), (mcx + 68, mcy + 68)], outline='#00E5FF', width=2)
    
    # Player marker
    draw.polygon([(mcx + 8, mcy - 8), (mcx - 4, mcy - 2), (mcx - 2, mcy + 2), (mcx - 8, mcy + 8)], fill='#00E5FF')

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


# ==============================================================================
# 5. SCREENSHOT 05: IN-APP GITHUB RELEASES UPDATER (800 x 1100)
# ==============================================================================

def generate_screenshot_05():
    print("Generating Screenshot 05: In-App GitHub Releases Updater...")
    W, H = 800, 1100
    img = Image.new('RGB', (W, H), '#0A0D12')
    draw = ImageDraw.Draw(img)

    # Modal Box
    mx, my, mw, mh = 40, 70, 720, 960
    draw.rounded_rectangle([(mx, my), (mx + mw, my + mh)], radius=12, fill='#0D1117', outline='#21262D', width=2)

    # Modal Header
    draw.text((mx + 28, my + 28), "SYSTEM UPDATER", fill='#00E5FF', font=FONT_HEAD)
    draw.text((mx + 28, my + 52), "Direct OTA channel connected to GitHub Releases API", fill='#8B949E', font=FONT_TEXT)
    draw.text((mx + mw - 40, my + 28), "✕", fill='#8B949E', font=FONT_HEAD)

    # 1. Current Build Status Card
    c1_y = my + 86
    draw.rounded_rectangle([(mx + 24, c1_y), (mx + mw - 24, c1_y + 72)], radius=8, fill='#121721', outline='#21262D', width=1)
    draw.text((mx + 40, c1_y + 16), "INSTALLED BUILD", fill='#8B949E', font=FONT_MONO_XS)
    draw.text((mx + 40, c1_y + 36), "1.0.0-tactical (Build 1)", fill='#F0F6FC', font=FONT_HEAD)

    draw.rounded_rectangle([(mx + mw - 190, c1_y + 20), (mx + mw - 40, c1_y + 52)], radius=4, fill='#0D1117', outline='#00E5FF', width=1)
    draw.text((mx + mw - 176, c1_y + 28), "GITHUB RELEASES", fill='#00E5FF', font=FONT_MONO_XS)

    # 2. Update Available Banner Card (Amber Border)
    c2_y = c1_y + 90
    draw.rounded_rectangle([(mx + 24, c2_y), (mx + mw - 24, c2_y + 110)], radius=8, fill='#121721', outline='#FFD600', width=1)
    draw.text((mx + 40, c2_y + 16), "NEW TACTICAL RELEASE AVAILABLE", fill='#FFD600', font=FONT_HEAD)
    draw.text((mx + mw - 140, c2_y + 16), "v1.0.1 ➔", fill='#00E5FF', font=FONT_HEAD)

    draw.text((mx + 40, c2_y + 42), "ReconDrive v1.0.1 — Tactical In-Vehicle Navigation", fill='#F0F6FC', font=FONT_HEAD)
    draw.text((mx + 40, c2_y + 68), "Asset: ReconDrive-v1.0.1-tactical.apk  •  Size: 46.5 MB  •  Published: Today", fill='#8B949E', font=FONT_TEXT)

    # 3. Live Streaming Download Progress Card
    c3_y = c2_y + 128
    draw.rounded_rectangle([(mx + 24, c3_y), (mx + mw - 24, c3_y + 110)], radius=8, fill='#161B22', outline='#00E5FF', width=1)
    draw.text((mx + 40, c3_y + 16), "DOWNLOADING APK PACKAGE...", fill='#00E5FF', font=FONT_HEAD)
    draw.text((mx + mw - 100, c3_y + 16), "68%", fill='#00E5FF', font=FONT_HEAD)

    # Progress bar
    pb_y = c3_y + 44
    draw.rectangle([(mx + 40, pb_y), (mx + mw - 40, pb_y + 10)], fill='#21262D')
    pb_active = (mw - 80) * 0.68
    draw.rectangle([(mx + 40, pb_y), (mx + 40 + pb_active, pb_y + 10)], fill='#00E5FF')

    draw.text((mx + 40, c3_y + 68), "31.6 MB / 46.5 MB  •  ReconDrive-v1.0.1-tactical.apk", fill='#8B949E', font=FONT_MONO_SM)

    # 4. Release Notes / Changelog Box
    c4_y = c3_y + 128
    draw.text((mx + 28, c4_y), "RELEASE INTEL & CHANGELOG", fill='#8B949E', font=FONT_MONO_XS)
    
    box_y = c4_y + 20
    draw.rounded_rectangle([(mx + 24, box_y), (mx + mw - 24, box_y + 240)], radius=8, fill='#121721', outline='#21262D', width=1)
    
    notes = [
        "• Added In-App GitHub Releases OTA Updater with real-time download streaming.",
        "• Direct integration with Android Package Installer via Secure FileProvider.",
        "• Enhanced Android Auto NavigationTemplate maneuver card padding and alignments.",
        "• Real-world Fog of War spatial persistence optimization with Room DB.",
        "• Decoupled 6 Map Themes and 6 Fog Skins with real-time in-car action cycling.",
        "• Added dynamic speed limits (50 km/h roundel) and lane guidance indicators."
    ]
    ny = box_y + 18
    for line in notes:
        draw.text((mx + 40, ny), line, fill='#F0F6FC' if line.startswith("• Added") else '#8B949E', font=FONT_TEXT)
        ny += 34

    # 5. Bottom Action Buttons
    act_y = box_y + 260
    draw.rounded_rectangle([(mx + 24, act_y), (mx + mw - 24, act_y + 54)], radius=8, fill='#00E5FF')
    txt_inst = "DOWNLOAD & INSTALL UPDATE"
    ti_w = draw.textlength(txt_inst, font=FONT_HEAD)
    draw.text((mx + 24 + (mw - 48 - ti_w) / 2, act_y + 18), txt_inst, fill='#0A0D12', font=FONT_HEAD)

    draw.rounded_rectangle([(mx + 24, act_y + 66), (mx + mw - 24, act_y + 110)], radius=8, fill='#121721', outline='#21262D', width=1)
    txt_gh = "VIEW RELEASE ON GITHUB"
    tg_w = draw.textlength(txt_gh, font=FONT_TEXT)
    draw.text((mx + 24 + (mw - 48 - tg_w) / 2, act_y + 80), txt_gh, fill='#8B949E', font=FONT_TEXT)

    out_path = os.path.join(OUTPUT_DIR, "screenshot_05_in_app_github_updater.png")
    img.save(out_path)
    print(f"Saved: {out_path} ({os.path.getsize(out_path)} bytes)")


# ==============================================================================
# 6. SCREENSHOT 06: INTERACTIVE GOOGLE MAPS-STYLE UK MAP (800 x 1400)
# ==============================================================================

def generate_screenshot_06():
    print("Generating Screenshot 06: Interactive Google Maps-style UK Map...")
    W, H = 800, 1400
    img = Image.new('RGB', (W, H), '#0A0D12')
    draw = ImageDraw.Draw(img)

    cx, cy = W / 2.0, H / 2.0

    # 1. Background Vector Water Features (River Thames & Estuary)
    thames_pts = [
        (0, cy + 120),
        (cx - 240, cy + 100),
        (cx - 100, cy + 80),
        (cx, cy + 70),
        (cx + 140, cy + 85),
        (cx + 280, cy + 130),
        (W, cy + 160)
    ]
    draw.line(thames_pts, fill='#06131D', width=28)
    draw.line(thames_pts, fill='#0C2236', width=14)

    # 2. Road Networks (UK Motorways M25 Ring, M1, M4, M20)
    # M25 Orbital Ring around London
    draw.ellipse([(cx - 280, cy - 220), (cx + 280, cy + 220)], outline='#1C2636', width=8)
    draw.ellipse([(cx - 280, cy - 220), (cx + 280, cy + 220)], outline='#00E5FF', width=3)

    # M1 Northwards
    draw.line([(cx - 50, cy - 60), (cx - 120, cy - 220), (cx - 180, 0)], fill='#00E5FF', width=4)
    # M4 Westwards
    draw.line([(cx - 60, cy + 20), (cx - 280, cy + 30), (0, cy + 40)], fill='#00E5FF', width=4)
    # M20 Eastwards to Dover
    draw.line([(cx + 80, cy + 60), (cx + 280, cy + 180), (W, cy + 260)], fill='#00E5FF', width=4)
    # A1(M) North
    draw.line([(cx, cy - 60), (cx + 20, cy - 220), (cx + 40, 0)], fill='#00A8CC', width=3)

    # Local secondary grid around Central London / Boroughs
    for r in range(-3, 4):
        draw.line([(cx - 180, cy + r * 45), (cx + 180, cy + r * 45)], fill='#16222F', width=2)
        draw.line([(cx + r * 50, cy - 140), (cx + r * 50, cy + 140)], fill='#16222F', width=2)

    # 3. Explored Fog of War Trail & Perimeter Shroud
    explored_trail = [
        (cx - 180, cy + 140),
        (cx - 80, cy + 70),
        (cx, cy),
        (cx + 80, cy - 60),
        (cx + 140, cy - 140)
    ]
    for gw in (18, 12, 6):
        draw.line(explored_trail, fill='#00E676' if gw == 6 else '#004D25', width=gw)

    # Cleared surveillance beam
    beam_r = 170.0
    for gr in (3, 2, 1):
        draw.ellipse([(cx - beam_r - gr, cy - beam_r - gr), (cx + beam_r + gr, cy + beam_r + gr)], outline='#00E5FF', width=1)
    draw.ellipse([(cx - beam_r, cy - beam_r), (cx + beam_r, cy + beam_r)], outline='#00E5FF', width=2)

    # 4. Target Waypoint (Amber destination pin & dashed route line)
    wp_x, wp_y = cx + 180, cy - 200
    # Dashed route
    draw.line([(cx, cy), (wp_x, wp_y)], fill='#FFD600', width=3)
    # Pin
    draw.ellipse([(wp_x - 14, wp_y - 14), (wp_x + 14, wp_y + 14)], fill='#FFD600')
    draw.ellipse([(wp_x - 24, wp_y - 24), (wp_x + 24, wp_y + 24)], outline='#FFD600', width=2)

    # 5. Vehicle Cursor (Cyan arrow at center)
    draw.ellipse([(cx - 24, cy - 24), (cx + 24, cy + 24)], fill='#00E5FF22')
    draw.polygon([(cx + 12, cy - 12), (cx - 6, cy - 2), (cx - 2, cy + 2), (cx - 12, cy + 12)], fill='#00E5FF')

    # 6. Highway Shields / Labels
    draw.rounded_rectangle([(cx - 290, cy - 10), (cx - 250, cy + 12)], radius=3, fill='#003366', outline='#00E5FF', width=1)
    draw.text((cx - 284, cy - 8), "M25", fill='#FFFFFF', font=FONT_MONO_XS)

    draw.rounded_rectangle([(cx - 150, cy - 150), (cx - 118, cy - 128)], radius=3, fill='#003366', outline='#00E5FF', width=1)
    draw.text((cx - 142, cy - 148), "M1", fill='#FFFFFF', font=FONT_MONO_XS)

    # 7. Top Floating Search Bar & City Jump Chips
    draw.rounded_rectangle([(24, 50), (W - 24, 102)], radius=8, fill='#121721', outline='#21262D', width=1)
    draw.text((44, 68), "🔍", fill='#00E5FF', font=FONT_SUB)
    draw.text((74, 68), "United Kingdom OSM • Tap map to explore & route", fill='#8B949E', font=FONT_TEXT)
    
    draw.rounded_rectangle([(W - 130, 60), (W - 36, 92)], radius=4, fill='#070A0F', outline='#00E676', width=1)
    draw.text((W - 118, 68), "UK OSM", fill='#00E676', font=FONT_MONO_XS)

    # Quick Jump Chips
    chips = ["🏛️ London", "🐝 Manchester", "🏭 Birmingham", "🏰 Edinburgh", "🐉 Cardiff", "⚓ Belfast"]
    ch_x = 24
    for chip in chips:
        ch_w = draw.textlength(chip, font=FONT_TEXT) + 24
        draw.rounded_rectangle([(ch_x, 116), (ch_x + ch_w, 150)], radius=16, fill='#121721', outline='#21262D', width=1)
        draw.text((ch_x + 12, 126), chip, fill='#F0F6FC' if "London" in chip else '#8B949E', font=FONT_TEXT)
        ch_x += ch_w + 8

    # 8. Right Floating Tactical Controls (Google Maps-style)
    fab_x = W - 74
    # Compass
    draw.ellipse([(fab_x, 180), (fab_x + 48, 228)], fill='#121721', outline='#21262D', width=1)
    draw.text((fab_x + 16, 194), "🧭", fill='#00E5FF', font=FONT_HEAD)

    # Fog Toggle
    draw.ellipse([(fab_x, 240), (fab_x + 48, 288)], fill='#00E5FF', outline='#00E5FF', width=1)
    draw.text((fab_x + 16, 254), "👁️", fill='#0A0D12', font=FONT_HEAD)

    # Skins
    draw.ellipse([(fab_x, 300), (fab_x + 48, 348)], fill='#121721', outline='#21262D', width=1)
    draw.text((fab_x + 16, 314), "🎨", fill='#00E5FF', font=FONT_HEAD)

    # Zoom (+)
    draw.rounded_rectangle([(fab_x + 4, 360), (fab_x + 44, 400)], radius=6, fill='#121721', outline='#21262D', width=1)
    draw.text((fab_x + 18, 370), "+", fill='#F0F6FC', font=FONT_TITLE)

    # Zoom (−)
    draw.rounded_rectangle([(fab_x + 4, 408), (fab_x + 44, 448)], radius=6, fill='#121721', outline='#21262D', width=1)
    draw.text((fab_x + 20, 416), "−", fill='#F0F6FC', font=FONT_TITLE)

    # Re-Center GPS Target FAB
    draw.ellipse([(fab_x - 6, 470), (fab_x + 54, 530)], fill='#00E5FF', outline='#00E5FF', width=2)
    draw.text((fab_x + 12, 488), "🎯", fill='#0A0D12', font=FONT_TITLE)

    # 9. Bottom Destination Card (Waypointed to M1/North)
    card_y = H - 250
    draw.rounded_rectangle([(24, card_y), (W - 24, card_y + 130)], radius=12, fill='#121721', outline='#00E5FF', width=1)
    draw.text((44, card_y + 16), "TACTICAL WAYPOINT // M1 CORRIDOR", fill='#FFD600', font=FONT_HEAD)
    draw.text((44, card_y + 40), "Distance: 14.2 km  •  ETA: ~18 min  •  Heading: 352° N", fill='#F0F6FC', font=FONT_MONO_SM)

    # Action Buttons inside card
    draw.rounded_rectangle([(44, card_y + 68), (W / 2 + 100, card_y + 112)], radius=6, fill='#00E5FF')
    draw.text((80, card_y + 80), "NAVIGATE TO TARGET", fill='#0A0D12', font=FONT_HEAD)

    draw.rounded_rectangle([(W / 2 + 116, card_y + 68), (W - 44, card_y + 112)], radius=6, fill='#161B22', outline='#21262D', width=1)
    draw.text((W / 2 + 150, card_y + 80), "CLEAR", fill='#8B949E', font=FONT_HEAD)

    # 10. Bottom Navigation Bar with MAP selected
    nav_y = H - 90
    draw.rectangle([(0, nav_y), (W, H)], fill='#0D1117')
    draw.line([(0, nav_y), (W, nav_y)], fill='#21262D', width=1)
    
    tabs = [
        ("HUD", "#8B949E", False),
        ("MAP", "#00E5FF", True),
        ("SKINS", "#8B949E", False),
        ("OFFLINE", "#8B949E", False),
    ]
    tab_step = W / len(tabs)
    for i, (tname, tcol, is_active) in enumerate(tabs):
        tx_c = i * tab_step + tab_step / 2
        tw = draw.textlength(tname, font=FONT_HEAD)
        draw.text((tx_c - tw / 2, nav_y + 16), tname, fill=tcol, font=FONT_HEAD)
        if is_active:
            draw.line([(tx_c - 16, nav_y + 36), (tx_c + 16, nav_y + 36)], fill='#00E5FF', width=2)

    # Android Home Gesture Bar
    draw.rounded_rectangle([(W / 2 - 60, H - 14), (W / 2 + 60, H - 10)], radius=2, fill='#484F58')

    out_path = os.path.join(OUTPUT_DIR, "screenshot_06_interactive_uk_map.png")
    img.save(out_path)
    print(f"Saved: {out_path} ({os.path.getsize(out_path)} bytes)")


if __name__ == '__main__':
    generate_screenshot_01()
    generate_screenshot_02()
    generate_screenshot_03()
    generate_screenshot_04()
    generate_screenshot_05()
    generate_screenshot_06()
    print("All 6 screenshots generated successfully!")
