package com.nutiteq.datasources.vector;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import com.nutiteq.components.CullState;
import com.nutiteq.components.Envelope;
import com.nutiteq.geometry.Geometry;
import com.nutiteq.geometry.Polygon;
import com.nutiteq.geometry.Polygon3D;
import com.nutiteq.geometry.VectorElement;
import com.nutiteq.log.Log;
import com.nutiteq.projections.Projection;
import com.nutiteq.roofs.FlatRoof;
import com.nutiteq.roofs.GabledRoof;
import com.nutiteq.roofs.HippedRoof;
import com.nutiteq.roofs.Polygon3DRoof;
import com.nutiteq.roofs.PyramidalRoof;
import com.nutiteq.roofs.Roof;
import com.nutiteq.style.Polygon3DStyle;
import com.nutiteq.style.StyleSet;
import com.nutiteq.ui.DefaultLabel;
import com.nutiteq.vectordatasources.AbstractVectorDataSource;
import com.nutiteq.vectordatasources.VectorDataSource;

/**
 * Converts objects to 3D roof objects
 * 
 * @author vendik
 *
 */
public class PolygonRoof3DDataSource<T extends VectorElement> extends AbstractVectorDataSource<Polygon3D> {

    // maximum roof height in meters
    private static final float MAX_ROOF_HEIGHT = 9.0f;

    // this height modifier depends on map projection, and latitude. In case of EPSG3857, it should be 1/cos(latitude) or 1, depending if height has been  'precorrected' or not
    private static final float HEIGHT_ADJUST = 1.0f;

    // multiplier to convert discrete levels to height (in meters) 
    private static final float LEVELS_TO_HEIGHT = 5.0f * HEIGHT_ADJUST;

    private static final HashMap<String, Integer> colorNames = new HashMap<String, Integer>();

    static {
        colorNames.put("black", 0xFF000000);
        colorNames.put("gray", 0xFF808080);
        colorNames.put("maroon", 0xFF800000);
        colorNames.put("olive", 0xFF808000);
        colorNames.put("green", 0xFF008000);
        colorNames.put("teal", 0xFF008080);
        colorNames.put("navy", 0xFF000080);
        colorNames.put("purple", 0xFF800080);

        colorNames.put("white", 0xFFFFFFFF);
        colorNames.put("silver", 0xFFC0C0C0);
        colorNames.put("red", 0xFFFF0000);
        colorNames.put("yellow", 0xFFFFFF00);
        colorNames.put("lime", 0xFF00FF00);
        colorNames.put("aqua", 0xFF00FFFF);
        colorNames.put("blue", 0xFF0000FF);
        colorNames.put("fuchsia", 0xFFFF00FF);
        colorNames.put("brown", 0xFFD2B48C);
        colorNames.put("light_green", 0xFFDBDB70);
        colorNames.put("violet", 0xFFDB7093);
        colorNames.put("pink", 0xFFEEA2AD);
        colorNames.put("orange", 0xFFCD3700);
    }

    private StyleSet<Polygon3DStyle> styleSet;
    private int minZoom;
    private int maxObjects;
    private float height;
    private Roof roofShape;
    private int color;
    private int roofColor;

    private VectorDataSource<Geometry> dataSource;

    /**
     * Constructor for layer with 3D OSM building boxes
     * 
     * @param proj Projection, usually EPSG3857()
     * @param height default height for buildings, unless they have height tag set
     * @param maxObjects limits number of objects per network call. Btw, the server has "largest objects first" order
     * @param styleSet defines visual styles
     */
    public PolygonRoof3DDataSource(Projection proj, VectorDataSource<Geometry> dataSource, float height, Roof roofShape, int color, int roofColor, 
            int maxObjects, StyleSet<Polygon3DStyle> styleSet) {
        super(proj);
        this.dataSource = dataSource;
        this.styleSet = styleSet;
        this.maxObjects = maxObjects;
        this.height = height;
        this.roofShape = roofShape;
        this.color = color;
        this.roofColor = roofColor;
        minZoom = styleSet.getFirstNonNullZoomStyleZoom();
    }

    @Override
    public Envelope getDataExtent() {
        return null; // TODO: implement
    }

    @Override
    public Collection<Polygon3D> loadElements(CullState cullState) {
        if (cullState.zoom < minZoom) {
            return null;
        }

        Collection<Geometry> polygons = dataSource.loadElements(cullState);
        return convert3D(polygons, cullState.zoom);
    }

    public Collection<Polygon3D> convert3D(Collection<Geometry> polygons, int zoom) {
        long start = System.currentTimeMillis();
        List<Polygon3D> polygons3D = new LinkedList<Polygon3D>();
        for (Geometry geometry : polygons) {

            // parse address and name for label
            @SuppressWarnings("unchecked")
            final Map<String, String> userData = (Map<String, String>) geometry.userData;
            String name = userData.get("NAME");
            String type = userData.get("TYPE");
            String address = userData.get("FULL");

            float height = parseHeight(userData.get("HEIGHT"), -1);
            if (height < 0) {
                height = parseLevelsHeight(userData.get("BUILDING:L"), this.height);
            }
            float roofHeight = parseHeight(userData.get("ROOF:HEIGH"), -1);
            if (roofHeight < 0) {
                if(userData.get("ROOF:LEVEL") != null){
                    // default roof height is 1/3 of building height, but no more than MAX_ROOF_HEIGHT
                    roofHeight = parseLevelsHeight(userData.get("ROOF:LEVEL"), Math.min(height / 3, MAX_ROOF_HEIGHT));

                    // adjust building height: add roof height if levels were given 
                    height += roofHeight; 
                }else{
                    roofHeight = Math.min(height / 3, MAX_ROOF_HEIGHT);
                }
            }
            int color = parseColor(userData.get("BUILDING:C"), this.color);
            int roofColor = parseColor(userData.get("ROOF:COLOU"), this.roofColor);
            boolean roofAlongLongSide = parseRoofOrientation(userData.get("ROOF:ORIEN"), this.roofShape.getAlongLongSide());
            Roof roofShape; 
            if (roofHeight > 0.0f) {
                roofShape = parseRoofShape(userData.get("RSHAPE"), roofHeight, roofAlongLongSide, this.roofShape);
            } else {
                roofShape = new FlatRoof();
            }

            float minHeight = parseHeight(userData.get("MINHEIGHT"), -1);
            if (minHeight < 0) {
                minHeight = parseLevelsHeight(userData.get("BUILDING:M"), 0);
            }

            DefaultLabel label = null;
            if ((name == null || name.equals("")) && address != null && address.length() > 0) {
                label = new DefaultLabel(address);
            }
            if (name != null && name.length() > 0 && (address == null || address.equals("")) ) {
                label = new DefaultLabel(name);
            }
            if (name != null && address != null && address.length() > 0 && name.length() > 0) {
                label = new DefaultLabel(name, address);
            }

            Log.debug("Polygon3D OSM. Name: " + name + " type: " + type + " addr: " + address + 
                    " height: " + height + " roof height: " + roofHeight + 
                    " color: " + color + " roof color: " + roofColor + " roof shape: " + roofShape.getClass().getSimpleName());

            // Create 3Dpolygon
            Polygon3D polygon3D = new Polygon3DRoof(((Polygon)geometry).getVertexList(), ((Polygon) geometry).getHolePolygonList(), 
                    height, minHeight, roofShape, color, roofColor, label, styleSet, userData);
            polygon3D.setId(geometry.getId());
            polygons3D.add(polygon3D);
        }
        Log.debug("Triangulation time: " + (System.currentTimeMillis() - start));
        return polygons3D;
    }

    private boolean parseRoofOrientation(String roofOrientationStr, boolean defaultAlongLongSide) {
        if(roofOrientationStr != null && roofOrientationStr.trim().length() > 0) {
            if (roofOrientationStr.equals("along")) {
                return true;
            } else if (roofOrientationStr.equals("across")) {
                return false;
            }
            Log.error("Failed to parse roof orientation from: " + roofOrientationStr);
        }
        return defaultAlongLongSide;
    }

    private float parseHeight(String heightStr, float defaultHeight) {
        if(heightStr != null && heightStr.trim().length() > 0){
            float height;
            // Change unit if needed
            try {
                if(heightStr.contains(" ")){
                    String[] parts = heightStr.split(" ");
                    height = Float.parseFloat(parts[0]);
                    height = convertToMeters(height,parts[1]);
                }else{
                    height = Float.parseFloat(heightStr);
                }
                height = HEIGHT_ADJUST * height;
                return height;
            } catch (Exception e) {
                Log.error("Failed to parse height from: " + heightStr);
            }
        }
        return defaultHeight;
    }

    private float parseLevelsHeight(String levelsStr, float defaultHeight) {
        if(levelsStr != null && levelsStr.trim().length() > 0){
            try {
                float height = Integer.parseInt(levelsStr) * LEVELS_TO_HEIGHT;
                return height;
            } catch (Exception e) {
                Log.error("Failed to parse levels height from: " + levelsStr);
            }
        }
        return defaultHeight;
    }

    private float convertToMeters(float hVal, String unit) {
        if(unit.equals("m")){
            return hVal;
        }
        if(unit.equals("ft")){
            return hVal * 0.3048f;
        }
        if(unit.equals("yd")){
            return hVal * 0.9144f;
        }
        return hVal;
    }

    private int parseColor(String colorStr, int defaultColor) {
        if (colorStr != null && colorStr.trim().length() > 0) {
            try {
                int color;
                if (colorStr.charAt(0) == '#') {
                    color = Integer.parseInt(colorStr);
                } else {
                    color = colorNames.get(colorStr);
                }
                return color;
            } catch (Exception e) {
                Log.error("Failed to parse color from: " + colorStr);
            }
        } 
        return defaultColor;
    }

    private Roof parseRoofShape(String roofShapeStr, float roofHeight, boolean alongLongSide, Roof defaultRoofShape) {
        if (roofShapeStr != null && roofShapeStr.trim().length() > 0) {
            if (roofShapeStr.equals("gabled")) {
                return new GabledRoof(roofHeight, alongLongSide);
            } else if (roofShapeStr.equals("hipped")) {
                return new HippedRoof(roofHeight, alongLongSide);
            } else if (roofShapeStr.equals("pyramidial")) {
                return new PyramidalRoof(roofHeight, alongLongSide);
            } else if (roofShapeStr.equals("flat")) {
                return new FlatRoof();
            } 

            Log.error("Failed to parse roof shape: " + roofShapeStr);
        }

        return defaultRoofShape;
    }

}
