package com.nutiteq.advancedmap.activity;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.View;
import android.widget.ZoomControls;

import com.nutiteq.MapView;
import com.nutiteq.advancedmap.R;
import com.nutiteq.components.Bounds;
import com.nutiteq.components.Components;
import com.nutiteq.components.CullState;
import com.nutiteq.components.Envelope;
import com.nutiteq.components.MapPos;
import com.nutiteq.components.Options;
import com.nutiteq.components.Range;
import com.nutiteq.datasources.raster.MBTilesRasterDataSource;
import com.nutiteq.datasources.vector.OSMPolygon3DDataSource;
import com.nutiteq.datasources.vector.PolygonRoof3DDataSource;
import com.nutiteq.datasources.vector.Spatialite3DPolygonDataSource;
import com.nutiteq.datasources.vector.SpatialiteDataSource;
import com.nutiteq.geometry.Geometry;
import com.nutiteq.geometry.Line;
import com.nutiteq.geometry.Polygon3D;
import com.nutiteq.layers.raster.UTFGridRasterLayer;
import com.nutiteq.layers.vector.deprecated.SpatialiteLayer;
import com.nutiteq.layers.vector.deprecated.SpatialiteTextLayer;
import com.nutiteq.log.Log;
import com.nutiteq.projections.EPSG3857;
import com.nutiteq.rasterdatasources.HTTPRasterDataSource;
import com.nutiteq.rasterdatasources.RasterDataSource;
import com.nutiteq.rasterlayers.RasterLayer;
import com.nutiteq.roofs.FlatRoof;
import com.nutiteq.style.LabelStyle;
import com.nutiteq.style.LineStyle;
import com.nutiteq.style.PointStyle;
import com.nutiteq.style.Polygon3DStyle;
import com.nutiteq.style.PolygonStyle;
import com.nutiteq.style.StyleSet;
import com.nutiteq.style.TextStyle;
import com.nutiteq.ui.DefaultLabel;
import com.nutiteq.ui.Label;
import com.nutiteq.utils.Const;
import com.nutiteq.utils.UnscaledBitmapLoader;
import com.nutiteq.vectordatasources.VectorDataSource;
import com.nutiteq.vectorlayers.GeometryLayer;
import com.nutiteq.vectorlayers.Polygon3DLayer;
import com.nutiteq.vectorlayers.VectorLayer;

/**
 * Basic map, same as HelloMap
 * 
 * Just defines and configures map with useful settings.
 *
 * Used layer(s):
 *  RasterLayer with TMS tile source for base map
 * 
 * @author jaak
 *
 */
public class PipesMapActivity extends Activity {

    private static final String SPATIALITE_FILE = "/sdcard/mapxt/spatialite/tll_vesi.sqlite";
    // Limit for the number of vector elements that are loaded
    private static final int MAX_ELEMENTS = 2000;
    private StyleSet<PointStyle> pointStyleSet;
    private StyleSet<LineStyle> lineStyleSet;
    protected StyleSet<LineStyle> lineStyleSet2;
    private StyleSet<PolygonStyle> polygonStyleSet;
    private StyleSet<Polygon3DStyle> polygon3DStyleSet;
    private LabelStyle labelStyle;
    
    // Default OSM building height in meters
    private static final float DEFAULT_BUILDING_HEIGHT = 18.0f;
    private static final int NR_OF_CIRCLE_VERTS = 12;
    private static final String MBTILES_FILE = "/sdcard/mapxt/mbtiles/tallinn_orto.mbtiles";
    private static final String OSM_BUILDINGS_EE = "/sdcard/mapxt/spatialite/tll_buildings.sqlite"; 
    
    private MapView mapView;
    private float dpi;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        dpi = metrics.density;

        
        setContentView(R.layout.main);

       Log.enableAll(); 
        
        // 1. Get the MapView from the Layout xml - mandatory
        mapView = (MapView) findViewById(R.id.mapView);

        // Optional, but very useful: restore map state during device rotation,
        // it is saved in onRetainNonConfigurationInstance() below
        Components retainObject = (Components) getLastNonConfigurationInstance();
        if (retainObject != null) {
            // just restore configuration, skip other initializations
            mapView.setComponents(retainObject);
            return;
        } else {
            // 2. create and set MapView components - mandatory
            Components components = new Components();
            mapView.setComponents(components);
        }


        // 3. Define map layer for basemap - mandatory.
        // Here we use MapQuest open tiles
        // Almost all online tiled maps use EPSG3857 projection.
        
        
//        RasterDataSource dataSource = new HTTPRasterDataSource(new EPSG3857(), 0, 18, "http://ecn.t3.tiles.virtualearth.net/tiles/r{quadkey}.png?g=1&mkt=en-US&shading=hill&n=z");
//        RasterDataSource dataSource = new HTTPRasterDataSource(new EPSG3857(), 0, 19, "http://ecn.t3.tiles.virtualearth.net/tiles/a{quadkey}.jpeg?g=1&mkt=en-US");
        try {
            MBTilesRasterDataSource dataSource = new MBTilesRasterDataSource(new EPSG3857(), 0, 19, MBTILES_FILE, false, this);

            RasterLayer mapLayer = new RasterLayer(dataSource, 1);
            mapView.getLayers().setBaseLayer(mapLayer);

        } catch (IOException e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
        }
//        UTFGridRasterLayer dbLayer = new UTFGridRasterLayer(dataSource, dataSource, MBTILES_FILE.hashCode());
//        mapView.getLayers().setBaseLayer(dbLayer);


        // Location: Estonia
        mapView.setFocusPoint(mapView.getLayers().getBaseLayer().getProjection().fromWgs84(24.5f, 58.3f));

        // rotation - 0 = north-up
        mapView.setMapRotation(0f);
        // zoom - 0 = world, like on most web maps
        mapView.setZoom(15.0f);
        // tilt means perspective view. Default is 90 degrees for "normal" 2D map view, minimum allowed is 30 degrees.
        mapView.setTilt(90.0f);

        // Activate some mapview options to make it smoother - optional
        mapView.getOptions().setPreloading(true);
        mapView.getOptions().setSeamlessHorizontalPan(true);
        mapView.getOptions().setTileFading(true);
        mapView.getOptions().setKineticPanning(true);
        mapView.getOptions().setDoubleClickZoomIn(true);
        mapView.getOptions().setDualClickZoomOut(true);

        mapView.getOptions().setVectorElementZRange(new Range(-100,100));
        
        // set sky bitmap - optional, default - white
        mapView.getOptions().setSkyDrawMode(Options.DRAW_BITMAP);
        mapView.getOptions().setSkyOffset(4.86f);
        mapView.getOptions().setSkyBitmap(
                UnscaledBitmapLoader.decodeResource(getResources(),
                        R.drawable.sky_small));

        // Map background, visible if no map tiles loaded - optional, default - white
        mapView.getOptions().setBackgroundPlaneDrawMode(Options.DRAW_BITMAP);
        mapView.getOptions().setBackgroundPlaneBitmap(
                UnscaledBitmapLoader.decodeResource(getResources(),
                        R.drawable.background_plane));
        mapView.getOptions().setClearColor(Color.WHITE);

        // configure texture caching - optional, suggested
        mapView.getOptions().setTextureMemoryCacheSize(20 * 1024 * 1024);
        mapView.getOptions().setCompressedMemoryCacheSize(8 * 1024 * 1024);

        // define online map persistent caching - optional, suggested. Default - no caching
        mapView.getOptions().setPersistentCachePath(this.getDatabasePath("mapcache").getPath());
        // set persistent raster cache limit to 100MB
        mapView.getOptions().setPersistentCacheSize(100 * 1024 * 1024);

        // 4. zoom buttons using Android widgets - optional
        // get the zoomcontrols that was defined in main.xml
        ZoomControls zoomControls = (ZoomControls) findViewById(R.id.zoomcontrols);
        // set zoomcontrols listeners to enable zooming
        zoomControls.setOnZoomInClickListener(new View.OnClickListener() {
            public void onClick(final View v) {
                mapView.zoomIn();
            }
        });
        zoomControls.setOnZoomOutClickListener(new View.OnClickListener() {
            public void onClick(final View v) {
                mapView.zoomOut();
            }
        });
        
        
        createStyleSets();
        try {
            addSpatiaLiteTable(SPATIALITE_FILE, "SewerPipesLine", "Geometry");
            addSpatiaLiteTableManhole(SPATIALITE_FILE, "SewerManholesPoint", "Geometry");
            
            /*
            SpatialiteDataSource overlandSource = new SpatialiteDataSource(new EPSG3857(), SPATIALITE_FILE, "SewerPipesLine", "Geometry", null, null){
                @Override
                protected Label createLabel(Map<String, String> userData) {
                    return PipesMapActivity.this.createLabel(userData);
                }

                @Override
                protected StyleSet<PointStyle> createPointStyleSet(Map<String, String> userData, int zoom) {
                    return pointStyleSet;
                }

                @Override
                protected StyleSet<LineStyle> createLineStyleSet(Map<String, String> userData, int zoom) {
                    return lineStyleSet2;
                }

                @Override
                protected StyleSet<PolygonStyle> createPolygonStyleSet(Map<String, String> userData, int zoom) {
                    return polygonStyleSet;
                }
            };

            overlandSource.setMaxElements(MAX_ELEMENTS);
            
            mapView.getLayers().addLayer(new GeometryLayer(overlandSource));
            */
            
            SpatialiteLayer overlandLayer = new SpatialiteLayer(new EPSG3857(), SPATIALITE_FILE, "SewerPipesLine", "Geometry", null, MAX_ELEMENTS, pointStyleSet, lineStyleSet2, polygonStyleSet);
            mapView.getLayers().addLayer(overlandLayer);
            
            SpatialiteTextLayer overlandTextLayer = new SpatialiteTextLayer(new EPSG3857(), overlandLayer, "PIPE_ID"){

                private StyleSet<TextStyle> styleSetRoad = new StyleSet<TextStyle>(
                        TextStyle.builder().setAllowOverlap(true)
                            .setOrientation(TextStyle.GROUND_ORIENTATION)
                            .setAnchorY(TextStyle.CENTER)
                            .setOffset3DZ(0.0f)
                            .setSize((int) (18 * dpi)).build());
                
                @Override
                protected StyleSet<TextStyle> createStyleSet(Geometry feature, int zoom) {
                    return styleSetRoad;
                }
                
            };
          //  overlandTextLayer.setVisibleZoomRange(new Range(15,22));
            mapView.getLayers().addLayer(overlandTextLayer);

            addOsmPolygonLayerOffline();

            
        } catch (IOException e) {
            e.printStackTrace();
        } 
        
//       addOsmPolygonLayer();
       
        
    }

    @Override
    protected void onStart() {
        mapView.startMapping();
        super.onStart();
    }

    @Override
    protected void onStop() {
        super.onStop();
        mapView.stopMapping();
    }

    public MapView getMapView() {
        return mapView;
    }
    
    private void createStyleSets() {
        // set styles for all 3 object types: point, line and polygon
        int minZoom = 5;
        int color = Color.BLUE;

        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        float dpi = metrics.density;

        pointStyleSet = new StyleSet<PointStyle>();
        Bitmap pointMarker = UnscaledBitmapLoader.decodeResource(getResources(), R.drawable.point);
        PointStyle pointStyle = PointStyle.builder().setBitmap(pointMarker).setSize(0.05f).setColor(color).setPickingSize(0.2f).build();
        pointStyleSet.setZoomStyle(minZoom, pointStyle);

        lineStyleSet = new StyleSet<LineStyle>();
        LineStyle lineStyle = LineStyle.builder().setWidth(0.05f).setColor(color).build();
        lineStyleSet.setZoomStyle(minZoom, lineStyle);

        lineStyleSet2 = new StyleSet<LineStyle>();
        LineStyle lineStyle2 = LineStyle.builder().setWidth(0.03f).setColor(Color.GRAY).build();
        lineStyleSet2.setZoomStyle(minZoom, lineStyle2);

        
        polygonStyleSet = new StyleSet<PolygonStyle>(null);
        PolygonStyle polygonStyle = PolygonStyle.builder().setColor(color & 0x80FFFFFF).setLineStyle(lineStyle).build();
        polygonStyleSet.setZoomStyle(minZoom, polygonStyle);

        labelStyle = 
                LabelStyle.builder()
                .setEdgePadding((int) (12 * dpi))
                .setLinePadding((int) (6 * dpi))
                .setTitleFont(Typeface.create("Arial", Typeface.BOLD), (int) (16 * dpi))
                .setDescriptionFont(Typeface.create("Arial", Typeface.NORMAL), (int) (13 * dpi))
                .build();
        
        
        Polygon3DStyle polygon3DStyle = Polygon3DStyle.builder().setColor(Color.WHITE & 0xaaffffff).build();
        polygon3DStyleSet = new StyleSet<Polygon3DStyle>(null);
        polygon3DStyleSet.setZoomStyle(15, polygon3DStyle);
    }
    
    public void addSpatiaLiteTable(String spatialLite, String table, String column) throws IOException{

        SpatialiteDataSource dataSource = new SpatialiteDataSource(new EPSG3857(), spatialLite, table, column, null, null) {
            @Override
            protected Label createLabel(Map<String, String> userData) {
                return PipesMapActivity.this.createLabel(userData);
            }

            @Override
            protected StyleSet<PointStyle> createPointStyleSet(Map<String, String> userData, int zoom) {
                return pointStyleSet;
            }

            @Override
            protected StyleSet<LineStyle> createLineStyleSet(Map<String, String> userData, int zoom) {
                return lineStyleSet;
            }

            @Override
            protected StyleSet<PolygonStyle> createPolygonStyleSet(Map<String, String> userData, int zoom) {
                return polygonStyleSet;
            }

            @Override
            public Collection<Geometry> loadElements(CullState cullState) {
                Collection<Geometry> elements = super.loadElements(cullState);
                Collection<Geometry> newElements = new ArrayList<Geometry>();
                List<MapPos> coords;
                List<MapPos> newCoords = new ArrayList<MapPos>();
                
                for(Geometry element : elements){
                    Map<String, String> userData = (Map<String, String>) element.userData;
                    double startZ = 0;
                    double endZ = 0;
//                    Log.debug("loading "+Arrays.toString(userData.keySet().toArray(new String[0])));
                    if(userData.containsKey("start_z")){
                       if(userData.get("start_z") != null && userData.get("start_bootomz") != null)
                           startZ = Double.valueOf(userData.get("start_z")) - Double.valueOf(userData.get("start_bootomz"));
                       if(userData.get("end_z") != null && userData.get("end_bottomz") != null)
                           endZ = Double.valueOf(userData.get("end_z")) - Double.valueOf(userData.get("end_bottomz")); 
                    }
                 //   Log.debug("startZ "+startZ+" endZ "+endZ);
                    
                    newCoords.clear();
                    if(element instanceof Line){
                        coords = ((Line)element).getVertexList();
                        boolean first = false;
                        for(MapPos coord : coords){
                            double z = 0;
                            if(first){
                                if(startZ>0){
                                    z  = startZ;
                                }else{
                                    z = endZ;
                                }
                            }else{
                                if(endZ>0){
                                    z = endZ;
                                }else{
                                    z = startZ;
                                }
                            }
                            first = !first;
//                            Log.debug("z="+z);
                            MapPos newPos = new MapPos(coord.x, coord.y, -z * 5);
                            newCoords.add(newPos);
                        }
                        newElements.add(new Line(newCoords, element.getLabel(), (StyleSet<LineStyle>) element.getStyleSet(), element.userData));
//                        ((Line) element).setVertexList(newCoords);
                    }
                }
                
                return newElements;
            }
        };
        dataSource.setMaxElements(MAX_ELEMENTS);

        // define pixels and screen width for automatic polygon/line simplification
        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);   
        dataSource.setAutoSimplify(2, metrics.widthPixels);

        GeometryLayer spatialiteLayer = new GeometryLayer(dataSource);

        mapView.getLayers().addLayer(spatialiteLayer);

        Envelope extent = spatialiteLayer.getDataExtent();
        mapView.setBoundingBox(new Bounds(extent.minX, extent.maxY, extent.maxX, extent.minY), false);
    }
    
    public void addSpatiaLiteTableManhole(String spatialLite, String table, String column) throws IOException{

        Spatialite3DPolygonDataSource dataSource = new Spatialite3DPolygonDataSource(new EPSG3857(), spatialLite, table, column, null, null) {
            @Override
            protected Label createLabel(Map<String, String> userData) {
                return PipesMapActivity.this.createLabel(userData);
            }

            @Override
            protected Polygon3D createManHole(MapPos mapPos, Map<String, String> userData) {
                return PipesMapActivity.this.createManHole(mapPos, userData);
            }

        };
        dataSource.setMaxElements(MAX_ELEMENTS);

        // define pixels and screen width for automatic polygon/line simplification
        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);   
        dataSource.setAutoSimplify(2, metrics.widthPixels);

        Polygon3DLayer spatialiteLayer = new Polygon3DLayer(dataSource);

        mapView.getLayers().addLayer(spatialiteLayer);

        Envelope extent = spatialiteLayer.getDataExtent();
        mapView.setBoundingBox(new Bounds(extent.minX, extent.maxY, extent.maxX, extent.minY), false);
    }
    
    protected Polygon3D createManHole(MapPos mapPos, Map<String, String> userData) {
       
        double circleScale = 1.0;
        float z = 0;
        
        try {
            if(userData.get("DIAMETER") != null)
                circleScale = Double.parseDouble(userData.get("DIAMETER")) / 1000.0d; // from mm to meters
            if(userData.get("Z") != null && userData.get("BOTTOMZ") != null)
                z = 0 - (Float.parseFloat(userData.get("Z")) -  Float.parseFloat(userData.get("BOTTOMZ"))) ; // height
        } catch (NumberFormatException e) {
            e.printStackTrace();
        }
        List<MapPos> circleVerts = new ArrayList<MapPos>(NR_OF_CIRCLE_VERTS);
        
        for (float tsj = 0; tsj <= 360; tsj += 360 / NR_OF_CIRCLE_VERTS) {
            MapPos vertPos = new MapPos(circleScale * Math.cos(tsj * Const.DEG_TO_RAD) + mapPos.x, circleScale * Math.sin(tsj * Const.DEG_TO_RAD) + mapPos.y);
            circleVerts.add(vertPos);
        }

//        Log.debug("z="+z);
//        return new Polygon3DRoof(circleVerts, null, -z, 0, new FlatRoof(), Color.GRAY, Color.GRAY, createLabel(userData), polygon3DStyleSet, userData);
        return new Polygon3D(circleVerts, null, z/7, createLabel(userData), polygon3DStyleSet, userData);
    }

    private Label createLabel(Map<String, String> userData) {
        StringBuffer labelTxt = new StringBuffer();
        for(Map.Entry<String, String> entry : userData.entrySet()){
            labelTxt.append(entry.getKey() + ": " + entry.getValue() + "\n");
        }
        return new DefaultLabel("Data:", labelTxt.toString(), labelStyle);
    }
    
    // Load online simple building 3D boxes
    private void addOsmPolygonLayer() {
        // Set style visible from zoom 15
        // note: & 0xaaffffff makes the color a bit transparent
        Polygon3DStyle polygon3DStyle = Polygon3DStyle.builder().setColor(Color.WHITE & 0xaaffffff).build();
        StyleSet<Polygon3DStyle> polygon3DStyleSet = new StyleSet<Polygon3DStyle>(null);
        polygon3DStyleSet.setZoomStyle(15, polygon3DStyle);

        OSMPolygon3DDataSource dataSource = new OSMPolygon3DDataSource(new EPSG3857(), DEFAULT_BUILDING_HEIGHT, new FlatRoof(),  Color.WHITE, Color.GRAY, 1500, polygon3DStyleSet);
        Polygon3DLayer osm3dLayer = new Polygon3DLayer(dataSource);
        mapView.getLayers().addLayer(osm3dLayer);
    }


    // Load make 3D roof objects from DataSource
    private void addOsmPolygonLayerOffline() throws IOException {
        
        
        SpatialiteDataSource baseData = new SpatialiteDataSource(new EPSG3857(), OSM_BUILDINGS_EE, "osm_buildings", "Geometry", null, null) {
            @Override
            protected Label createLabel(Map<String, String> userData) {
                return null;
            }

            @Override
            protected StyleSet<PointStyle> createPointStyleSet(Map<String, String> userData, int zoom) {
                return pointStyleSet;
            }

            @Override
            protected StyleSet<LineStyle> createLineStyleSet(Map<String, String> userData, int zoom) {
                return lineStyleSet;
            }

            @Override
            protected StyleSet<PolygonStyle> createPolygonStyleSet(Map<String, String> userData, int zoom) {
                return polygonStyleSet;
            }
        };
        
        // Set style visible from zoom 15
        // note: & 0xaaffffff makes the color a bit transparent
        Polygon3DStyle polygon3DStyle = Polygon3DStyle.builder().setColor(Color.WHITE & 0xaaffffff).build();
        StyleSet<Polygon3DStyle> polygon3DStyleSet = new StyleSet<Polygon3DStyle>(null);
        polygon3DStyleSet.setZoomStyle(15, polygon3DStyle);

        PolygonRoof3DDataSource dataSource = new PolygonRoof3DDataSource(new EPSG3857(), baseData, DEFAULT_BUILDING_HEIGHT, new FlatRoof(),  Color.WHITE, Color.GRAY, 1500, polygon3DStyleSet);
        Polygon3DLayer osm3dLayer = new Polygon3DLayer(dataSource);
        osm3dLayer.setVisibleZoomRange(new Range (15,22));
        mapView.getLayers().addLayer(osm3dLayer);
    }


}

