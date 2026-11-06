package com.recondrive.app.map

/**
 * Tactical Dark OSM Vector Tile Style Definition
 * High-contrast matte obsidian background, tactical cyan roads, and amber navigation corridors.
 */
object TacticalMapStyle {

    const val TACTICAL_STYLE_JSON = """
    {
      "version": 8,
      "name": "ReconDrive Tactical Blackout",
      "sources": {
        "openmaptiles": {
          "type": "vector",
          "url": "https://demotiles.maplibre.org/tiles/tiles.json"
        }
      },
      "layers": [
        {
          "id": "background",
          "type": "background",
          "paint": {
            "background-color": "#0A0D12"
          }
        },
        {
          "id": "water",
          "type": "fill",
          "source": "openmaptiles",
          "source-layer": "water",
          "paint": {
            "fill-color": "#06131D",
            "fill-opacity": 0.9
          }
        },
        {
          "id": "landuse",
          "type": "fill",
          "source": "openmaptiles",
          "source-layer": "landuse",
          "paint": {
            "fill-color": "#0E141E",
            "fill-opacity": 0.5
          }
        },
        {
          "id": "roads_minor",
          "type": "line",
          "source": "openmaptiles",
          "source-layer": "transportation",
          "filter": ["all", ["!=", "class", "motorway"], ["!=", "class", "primary"]],
          "paint": {
            "line-color": "#1C2636",
            "line-width": 1.5
          }
        },
        {
          "id": "roads_major",
          "type": "line",
          "source": "openmaptiles",
          "source-layer": "transportation",
          "filter": ["any", ["==", "class", "primary"], ["==", "class", "trunk"]],
          "paint": {
            "line-color": "#00A8CC",
            "line-width": 2.5
          }
        },
        {
          "id": "roads_motorway",
          "type": "line",
          "source": "openmaptiles",
          "source-layer": "transportation",
          "filter": ["==", "class", "motorway"],
          "paint": {
            "line-color": "#00E5FF",
            "line-width": 3.5
          }
        },
        {
          "id": "road_labels",
          "type": "symbol",
          "source": "openmaptiles",
          "source-layer": "transportation_name",
          "layout": {
            "symbol-placement": "line",
            "text-field": "{name:latin}",
            "text-size": 10
          },
          "paint": {
            "text-color": "#8B949E",
            "text-halo-color": "#0A0D12",
            "text-halo-width": 1.5
          }
        }
      ]
    }
    """
}
