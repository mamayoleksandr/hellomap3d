package com.nutiteq.advancedmap.activity;

import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.EditText;
import android.widget.ZoomControls;

import com.nutiteq.MapView;
import com.nutiteq.advancedmap.R;
import com.nutiteq.components.Components;
import com.nutiteq.components.Envelope;
import com.nutiteq.components.MapPos;
import com.nutiteq.components.Options;
import com.nutiteq.db.DBLayer;
import com.nutiteq.filepicker.FilePickerActivity;
import com.nutiteq.geometry.Geometry;
import com.nutiteq.geometry.Point;
import com.nutiteq.layers.raster.TMSMapLayer;
//import com.nutiteq.layers.vector.GeoPackageDbHelper2;
import com.nutiteq.layers.vector.SpatialLiteDbHelper;
import com.nutiteq.layers.vector.SpatialiteLayer;
import com.nutiteq.layers.vector.SpatialiteTextLayer;
import com.nutiteq.log.Log;
import com.nutiteq.projections.EPSG3857;
import com.nutiteq.style.LineStyle;
import com.nutiteq.style.PointStyle;
import com.nutiteq.style.PolygonStyle;
import com.nutiteq.style.StyleSet;
import com.nutiteq.style.TextStyle;
import com.nutiteq.utils.UnscaledBitmapLoader;

/**
 * 
 * Demonstrates usage of SpatialiteLayer
 * 
 *  The application shows first list of tables in selected Spatialite database file, then opens file, 
 * loads bounds of selected table and recenters map accordingly 
 *  
 * The layer uses and requires custom compiled jsqlite (with spatialite) and proj.4 libraries via JNI
 *  
 * You need a Spatialite table file in SDCard. Up to version 4.x tables are supported.
 *  
 * See https://github.com/nutiteq/hellomap3d/wiki/Spatialite-layer for details and sample data downloads
 * 
 * @author jaak
 *
 */
public class SpatialiteMapActivity extends Activity implements FilePickerActivity {

	private static final int DIALOG_TABLE_LIST = 1;
    private static final int DIALOG_NO_TABLES = 2;
    private static final int DIALOG_ADD_SQL_LAYER=3;
    private static final String DEFAULT_SQL = "select rowid, HEX(AsBinary(transform(geometry,3857))) from ln_highway where name like 'Tallinn - Tartu%'";
    private MapView mapView;
    private String[] tableList = new String[1];
    private EPSG3857 proj;
    private SpatialLiteDbHelper spatialLite;
    private Map<String, DBLayer> dbMetaData;
    private float dpi;

    
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// calculate dpi for future use
        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        dpi = metrics.density;
		
		// spinner in status bar, for progress indication
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);

		setContentView(R.layout.main);

		// enable logging for troubleshooting - optional
		Log.enableAll();
		Log.setTag("spatialitemap");

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
		    // set stereo view: works if you rotate to landscape and device has HTC 3D or LG Real3D
		    mapView.setComponents(components);
		}
	
		// define map projection
		this.proj = new EPSG3857();
		
		TMSMapLayer mapLayer = new TMSMapLayer(this.proj, 0, 18, 1,
				"http://otile1.mqcdn.com/tiles/1.0.0/osm/", "/", ".png");

		mapView.getLayers().setBaseLayer(mapLayer);

		// set initial map view camera - optional. "World view" is default
		// Location: Estonia
        mapView.setFocusPoint(proj.fromWgs84(24.5f, 58.3f));

		// rotation - 0 = north-up
		mapView.setRotation(0f);
		// zoom - 0 = world, like on most web maps
		mapView.setZoom(10.0f);
        // tilt means perspective view. Default is 90 degrees for "normal" 2D map view, minimum allowed is 30 degrees.
		mapView.setTilt(90.0f);


		// Activate some mapview options to make it smoother - optional
		mapView.getOptions().setPreloading(false);
		mapView.getOptions().setSeamlessHorizontalPan(true);
		mapView.getOptions().setTileFading(false);
		mapView.getOptions().setKineticPanning(true);
		mapView.getOptions().setDoubleClickZoomIn(true);
		mapView.getOptions().setDualClickZoomOut(true);

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
		
		// read filename from extras
        Bundle b = getIntent().getExtras();
        String file = b.getString("selectedFile");
        
        showSpatialiteTableList(file);
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

    private void showSpatialiteTableList(String dbPath) {

        spatialLite = new SpatialLiteDbHelper(dbPath);
        dbMetaData = spatialLite.qrySpatialLayerMetadata();

        ArrayList<String> tables = new ArrayList<String>();
        
        for (String layerKey : dbMetaData.keySet()) {
            DBLayer layer = dbMetaData.get(layerKey);
            Log.debug("layer: " + layer.table + " " + layer.type + " geom:"
                    + layer.geomColumn+ " SRID: "+layer.srid);
            tables.add(layerKey);
        }

        Collections.sort(tables);
        
        if(tables.size() > 0){
            tableList = (String[]) tables.toArray(new String[0]);
            showDialog(DIALOG_TABLE_LIST);

        }else{
            showDialog(DIALOG_NO_TABLES);
        }

    }

    @Override
    protected Dialog onCreateDialog(int id) {
        final View view = getLayoutInflater().inflate(R.layout.dialog_sql, null);
        switch(id){
        case DIALOG_ADD_SQL_LAYER:
            return new AlertDialog.Builder(this)
            .setTitle("SQL:")
            .setView(view)
            .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
                    dialog.dismiss();
                    EditText editField = (EditText)view.findViewById(R.id.sql);
                    String sql = editField.getText().toString();
                    Log.debug("entered sql: "+sql);
                    addSpatiaLiteTable(0,sql, Color.RED);
                }
            }).create();

        case DIALOG_TABLE_LIST:
            return new AlertDialog.Builder(this)
            .setTitle("Select table:")
            .setSingleChoiceItems(tableList, 0, null)
            .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
                    dialog.dismiss();
                    int selectedPosition = ((AlertDialog) dialog)
                            .getListView().getCheckedItemPosition();
                    addSpatiaLiteTable(selectedPosition, null, Color.BLUE);
                }
            }).create();
            
        case DIALOG_NO_TABLES:
            return new AlertDialog.Builder(this)
            .setMessage("No geometry_columns or spatial_ref_sys metadata found. Check logcat for more details.")
            .setPositiveButton("Back", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
                    dialog.dismiss();
                    finish();
                  }
            }).create();
            
        }
        return null;
    }
    
	protected void addSqlLayer(String sql) {
        
        
    }

    public void addSpatiaLiteTable(int selectedPosition, String customSql, int color){
	    
	    // some general constants
        int minZoom = 0;
        int maxElements = 1000;
        
        // set styles for all 3 object types: point, line and polygon

        StyleSet<PointStyle> pointStyleSet = new StyleSet<PointStyle>();
        Bitmap pointMarker = UnscaledBitmapLoader.decodeResource(
                getResources(), R.drawable.point);
        PointStyle pointStyle = PointStyle.builder().setBitmap(pointMarker)
                .setSize(0.05f).setColor(color).setPickingSize(0.2f).build();
        pointStyleSet.setZoomStyle(minZoom, pointStyle);

        StyleSet<LineStyle> lineStyleSet = new StyleSet<LineStyle>();
        LineStyle lineStyle = LineStyle.builder().setWidth(0.05f)
                .setColor(color).build();
        lineStyleSet.setZoomStyle(minZoom, lineStyle);

        PolygonStyle polygonStyle = PolygonStyle.builder()
                .setColor(color & 0x80FFFFFF).setLineStyle(
                        LineStyle.builder().setWidth(0.05f).setColor(color).build()
                        ).build();
        StyleSet<PolygonStyle> polygonStyleSet = new StyleSet<PolygonStyle>(
                null);
        polygonStyleSet.setZoomStyle(minZoom, polygonStyle);
	    
        String geoColumn;
        String table;
        String[] tableKey = tableList[selectedPosition].split("\\.");
        table = tableKey[0];
        geoColumn = tableKey[1];
	    
        SpatialiteLayer spatialiteLayer = new SpatialiteLayer(proj, spatialLite, table,
                geoColumn, null, maxElements, pointStyleSet, lineStyleSet, polygonStyleSet);
        
        if(customSql != null){
            spatialiteLayer.setCustomSql(customSql);
        }
        
	    mapView.getLayers().addLayer(spatialiteLayer);

	    if(customSql == null){
	        // skip for custom SQL, does not work
	        
	        // find extent of the layer, zoom to it
	        Envelope extent = spatialiteLayer.getDataExtent();
	        
	        DisplayMetrics metrics = new DisplayMetrics();
	        getWindowManager().getDefaultDisplay().getMetrics(metrics);   
	        int screenHeight = metrics.heightPixels;
	        int screenWidth = metrics.widthPixels;

	        double zoom = Math.log((screenWidth * (Math.PI * 6378137.0f * 2.0f)) 
	                / ((extent.maxX-extent.minX) * 256.0)) / Math.log(2);
	        
	        MapPos centerPoint = new MapPos((extent.maxX+extent.minX)/2,(extent.maxY+extent.minY)/2);
	        Log.debug("found extent "+extent+", zoom "+zoom+", centerPoint "+centerPoint);

	        // define pixels and screen width for automatic polygon/line simplification
	        spatialiteLayer.setAutoSimplify(2,screenWidth);
	        
	        mapView.setZoom((float) zoom);
	        mapView.setFocusPoint(centerPoint); 
	    }
	    
	    // add text layer for names
	    SpatialiteTextLayer textLayer = new SpatialiteTextLayer(mapView.getLayers().getBaseLayer().getProjection(), spatialiteLayer) {
        
	        // text styling is defined in this class implementation
            private StyleSet<TextStyle> styleSetLines = new StyleSet<TextStyle>(
                    TextStyle.builder().setAllowOverlap(false)
                            .setOrientation(TextStyle.GROUND_ORIENTATION)
                            .setAnchorY(TextStyle.CENTER)
                            .setSize((int) (25 * dpi)).build());
            
            private StyleSet<TextStyle> styleSetPoints = new StyleSet<TextStyle>(
                    TextStyle
                            .builder()
                            .setAllowOverlap(false)
                            .setOrientation(TextStyle.CAMERA_BILLBOARD_ORIENTATION)
                            .setSize((int) (30 * dpi))
                            .setColor(Color.argb(255, 100, 100, 100))
                            .setPlacementPriority(5).build());

            @Override
            protected StyleSet<TextStyle> createStyleSet(Geometry feature,
                    int zoom) {
                if (feature instanceof Point) {
                    return styleSetPoints;
                }
                return styleSetLines;
            }

        };
        
        // 2. set properties for texts
        textLayer.setZOrdered(false);
        textLayer.setMaxVisibleElements(50);
        
        // 3. add layer
        mapView.getLayers().addLayer(textLayer);
	    
	    
	}
	
    public MapView getMapView() {
        return mapView;
    }

    
    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.spatialite, menu);
        return true;
    }
    
    @Override
    public boolean onMenuItemSelected(final int featureId, final MenuItem item) {

        switch (item.getItemId()) {

        // map types
        case R.id.sql:
            showDialog(DIALOG_ADD_SQL_LAYER);
        }
        return false;
    }
    
    @Override
    public String getFileSelectMessage() {
        return "Select Spatialite database file (.spatialite, .db, .sqlite)";
    }

    @Override
    public FileFilter getFileFilter() {
        return new FileFilter() {
            @Override
            public boolean accept(File file) {
                // accept only readable files
                if (file.canRead()) {
                    if (file.isDirectory()) {
                        // accept all directories
                        return true;
                    } else if (file.isFile()
                            && (file.getName().endsWith(".db")
                                    || file.getName().endsWith(".sqlite")
                                    || file.getName().endsWith(".geopackage")
                                    || file
                                    .getName().endsWith(".spatialite"))) {
                        // accept files with given extension
                        return true;
                    }
                }
                return false;
            };
        };
    }
    
}

