package usstatesstats;

import static java.lang.Math.random;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.ScaleTransition;
import javafx.animation.Timeline;
import javafx.application.Application;
import static javafx.application.Application.launch;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.scene.Scene;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ScrollPane.ScrollBarPolicy;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.effect.BlendMode;
import javafx.scene.effect.BoxBlur;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.StrokeType;
import javafx.scene.text.Text;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import javafx.util.Duration;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.midi.Sequence;
import javax.sound.midi.Sequencer;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;

public class USStatesStats extends Application
{
    private static final String APP_TITLE = "Jessica's Continental US Map";

    private static final int MAP_WIDTH = 1300;   //1300
    private static final int MAP_HEIGHT = 800;  //800
    private static final int VIEW_WIDTH = 1000;  //1000
    private static final int VIEW_HEIGHT = 680;  //680

    private static final int NO_STATES = 48;
    private static final String USSTATESMAPFILE = "/usstatesstats/map/states.xml";
    private static final String[] STATE_NAMES = new String[NO_STATES];
    private static final String[] STATE_COLORS = new String[NO_STATES];
    //We create three (3) sets of state polygons: first set is clickable for information; second set shows
    //the state boundary (outline) as placeholders for jigsaw puzzle, and is neither clickable nor draggable;
    //third set is for the jigsaw puzzle so is draggable.
    private static final Polygon[] STATE_POLYGONS = new Polygon[NO_STATES * 3];
    private static final int STATE_POLYGON_INFO = 0;
    private static final int STATE_POLYGON_OUTLINE = 1;
    private static final int STATE_POLYGON_DRAGGABLE = 2;

    private static final String MODE_INFORMATION = "Information Mode";
    private static final String MODE_JIGSAW_PUZZLE = "Jigsaw Puzzle Mode";
    private static boolean blnInformationMode = true;

    private static Button btnShuffle = null;

    private static final String MODE_BEGINNER = "Beginner";
    private static RadioButton rbBeginner = null;
    private static final String MODE_ADVANCED = "Advanced";
    private static RadioButton rbAdvanced = null;
    private static boolean blnBeginnerMode = true;

    private static Label lblStatus = null;

    private static Stage wikiStage = null;
    private static WebEngine wikiWE = null;
    private static Sequencer midiPlayer = null;

    private static int intStateSelectedOld = -1;
    private static int intStateBeingDragged = -1;
    private static int intStateCount = -1;

    @Override
    public void start(Stage stage)
    {
        //State polygons with latitudes and longitudes are stored in a XML file.
        loadMapXMLFile();

        //Create the webbrowser loaded with Wikipedia page for the selected state.
        createWiki(stage);

        //Create the MIDI Player to play MIDI sound (*.mid).
        createMIDIPlayer();

        //Create the pane to draw the state polygons.
        Pane pane = new Pane();
        pane.setPrefSize(MAP_WIDTH, MAP_HEIGHT);
        createAllStatePolygons();
        pane.getChildren().addAll(STATE_POLYGONS);

        //Create the scrollpane to hold the pane with the state polygons.
        ScrollPane scrollpane = new ScrollPane();
        setupScrollPane(pane, scrollpane);
        scrollpane.setContent(pane);

        //We also need to create the borderpane to hold the menu bar on the top, the scrollpane at the center,
        //and the statusbar at the bottom.
        BorderPane borderpane = new BorderPane();
        //All the action menus are on the top.
        borderpane.setTop(createTopbar(pane));
        //The scrollpane at the center.
        borderpane.setCenter(scrollpane);
        HBox statusbar = new HBox(12);
        lblStatus = new Label("Click on the map to start ...");
        configureBorder(statusbar);
        statusbar.getChildren().add(lblStatus);
        //Statusbar at the bottom.
        borderpane.setBottom(statusbar);

        //Finally we just add the borderpane and set up the stage.
        Scene scene = new Scene(new Group(), MAP_WIDTH, MAP_HEIGHT);
        scene.setRoot(borderpane);
        stage.setScene(scene);
        stage.setTitle(APP_TITLE);
        stage.setWidth(VIEW_WIDTH);
        stage.setHeight(VIEW_HEIGHT);
        stage.setOnCloseRequest(new EventHandler<WindowEvent>()
        {
            @Override
            public void handle(WindowEvent t)
            {
                stopMIDI();
                Platform.exit();
            }
        });
        stage.show();
    }

    private void setupScrollPane(Pane pane, ScrollPane scrollpane)
    {
        scrollpane.setHbarPolicy(ScrollBarPolicy.ALWAYS);
        scrollpane.setVbarPolicy(ScrollBarPolicy.ALWAYS);
        scrollpane.setHvalue(0.50);
        scrollpane.setVvalue(0.60);
        scrollpane.setOnMouseClicked(new EventHandler<MouseEvent>()
        {
            @Override
            public void handle(MouseEvent evt)
            {
                if (blnInformationMode)
                {
                    intStateBeingDragged = -1;
                    boolean blnStateClickedOn = false;
                    //We need to find out which state is being clicked on
                    //But first we need to find out the coordinates (xp, yp) of the click.
                    Bounds lb = pane.getLayoutBounds();
                    Bounds vb = scrollpane.getViewportBounds();
                    double valX = scrollpane.getHvalue() * (lb.getWidth() - vb.getWidth());
                    double valY = scrollpane.getVvalue() * (lb.getHeight() - vb.getHeight());
                    double xp = evt.getX() + (valX > 0 ? valX : 0);
                    double yp = evt.getY() + (valY > 0 ? valY : 0);
                    for (int i = 0; i < NO_STATES; i++)
                    {
                        //We find it when the state polygon contains (xp, yp).
                        if (STATE_POLYGONS[i].contains(xp, yp))
                        {
                            //Do we have to return the previously clicked state polygon to its normal color.
                            if (intStateSelectedOld >= 0)
                            {
                                STATE_POLYGONS[intStateSelectedOld].setFill(Color.web(STATE_COLORS[intStateSelectedOld]));
                            }
                            //Set the color of the state polygon being clicked on to white.
                            STATE_POLYGONS[i].setFill(Color.web("#ffffff"));
                            System.out.println(i + ", " + STATE_NAMES[i]);
                            lblStatus.setText(STATE_NAMES[i]);
                            intStateSelectedOld = i;
                            blnStateClickedOn = true;
                            //Play background music
                            if (STATE_NAMES[i].equalsIgnoreCase("TEXAS")) playMIDI("/usstatesstats/midi/tx.mid");
                            else playMIDI("/usstatesstats/midi/star_spangled_banner.mid");
                            //Show the wikipedia page about the state
                            wikiWE.load("https://en.wikipedia.org/wiki/" + STATE_NAMES[i]);
                            wikiStage.show();
                            //Once we found the state, no need to continue the for loop so just break out.
                            break;
                        }
                    }
                    //If we only clicked on the blank space, three things to do: 1) return the previously clicked state
                    //polygon to its normal color; 2) hide wikipedia; 3) stop the background music.
                    if (!blnStateClickedOn)
                    {
                        if (intStateSelectedOld >= 0)
                        {
                            STATE_POLYGONS[intStateSelectedOld].setFill(Color.web(STATE_COLORS[intStateSelectedOld]));
                            lblStatus.setText("");
                            wikiStage.hide();
                        }
                        stopMIDI();
                    }
                }
            }
        });
        //In our case here two things could trigger MOUSE_RELEASED event: 1) Clicked (pressed and released);
        //2) Dragged (pressed, dragged, and released).
        scrollpane.addEventFilter(MouseEvent.MOUSE_RELEASED, new EventHandler<MouseEvent>()
        {
            @Override
            public void handle(final MouseEvent mouseEvent)
            {
                if (!blnInformationMode)
                {
                    int k = -1;
                    //We need to find out which state is being targeted (or to be released to)
                    //But first we need to find out the coordinates (xp, yp) of the released mouse.
                    Bounds lb = pane.getLayoutBounds();
                    Bounds vb = scrollpane.getViewportBounds();
                    double valX = scrollpane.getHvalue() * (lb.getWidth() - vb.getWidth());
                    double valY = scrollpane.getVvalue() * (lb.getHeight() - vb.getHeight());
                    double xp = mouseEvent.getX() + (valX > 0 ? valX : 0);
                    double yp = mouseEvent.getY() + (valY > 0 ? valY : 0);
                    for (int i = 0; i < NO_STATES; i++)
                    {
                        //We find it when the state polygon contains (xp, yp).
                        if (STATE_POLYGONS[NO_STATES + i].contains(xp, yp))
                        {
                            k = i;
                            //Once we found it, simply break the for loop
                            break;
                        }
                    }
                    //Now we need to make sure the state polygon being dragged and the targeted state polygon are the same
                    if (k >= 0 && STATE_POLYGONS[NO_STATES + k].getId().equals(STATE_POLYGONS[2 * NO_STATES + intStateBeingDragged].getId()))
                    {
                        //If they are the same, we simply fit the dragged state polygon right in place, but it is no longer draggable.
                        //System.out.println("Good, " + intStateBeingDragged + ", " + STATE_POLYGONS[NO_STATES + k]);
                        STATE_POLYGONS[2 * NO_STATES + intStateBeingDragged] = new Polygon();
                        STATE_POLYGONS[2 * NO_STATES + intStateBeingDragged].getPoints().addAll(STATE_POLYGONS[NO_STATES + k].getPoints());
                        makeStatePolygon(intStateBeingDragged, STATE_POLYGON_DRAGGABLE);
                        STATE_POLYGONS[2 * NO_STATES + intStateBeingDragged].setVisible(true);
                        pane.getChildren().clear();
                        pane.getChildren().addAll(STATE_POLYGONS);
                        intStateBeingDragged = -1;
                        //Increase the count so we can show some progress and find if we are done with the puzzle.
                        intStateCount++;
                        //System.out.println("Found: " + intStateCount);
                        lblStatus.setText("Found: " + STATE_NAMES[k] + "; total: " + (intStateCount + 1));
                        showProgress(pane);
                    }
                }
            }
        });
    }

    private HBox createTopbar(Pane pane)
    {
        HBox topbar = new HBox(12);
        final ToggleGroup tg1 = new ToggleGroup();
        RadioButton btnInformation = new RadioButton(MODE_INFORMATION);
        btnInformation.setToggleGroup(tg1);
        btnInformation.setUserData(MODE_INFORMATION);
        btnInformation.setSelected(true);
        blnInformationMode = true;
        RadioButton btnJigsawPuzzle = new RadioButton(MODE_JIGSAW_PUZZLE);
        btnJigsawPuzzle.setUserData(MODE_JIGSAW_PUZZLE);
        btnJigsawPuzzle.setToggleGroup(tg1);
        btnJigsawPuzzle.setPadding(new Insets(0, 50, 0, 0));
        tg1.selectedToggleProperty().addListener(new ChangeListener<Toggle>()
        {
            @Override
            public void changed(ObservableValue<? extends Toggle> ov, Toggle old_toggle, Toggle new_toggle)
            {
                if (tg1.getSelectedToggle() != null)
                {
                    if (tg1.getSelectedToggle().getUserData().equals(MODE_INFORMATION))
                    {
                        //Information Mode is selected: make clickable state polygons visible, hide all others.
                        btnShuffle.setVisible(false);
                        rbBeginner.setVisible(false);
                        rbAdvanced.setVisible(false);
                        blnInformationMode = true;
                        for (int i = 0; i < NO_STATES; i++)
                        {
                            STATE_POLYGONS[i].setVisible(true);
                            STATE_POLYGONS[NO_STATES + i].setVisible(false);
                            STATE_POLYGONS[2 * NO_STATES + i].setVisible(false);
                        }
                        lblStatus.setText("Click on the map to continue");
                    } else {
                        //Jigsaw Puzzle Mode is selected:
                        //1) return the previously clicked state polygon to its normal color if we have one;
                        //2) hide wikipedia page;
                        //3) hide clicked state polygons (first set);
                        //4) show outline state polygons (second set);
                        //5) initialize or recreate draggable state polygons (third set) and make them visible;
                        //6) remove all (three sets of) the old state polygons and add all the new ones;
                        //7) reset the counters.
                        if (intStateSelectedOld >= 0)
                        {
                            STATE_POLYGONS[intStateSelectedOld].setFill(Color.web(STATE_COLORS[intStateSelectedOld]));
                        }
                        if (wikiStage.isShowing()) wikiStage.hide();
                        btnShuffle.setVisible(true);
                        rbBeginner.setVisible(true);
                        rbAdvanced.setVisible(true);
                        blnInformationMode = false;
                        for (int i = 0; i < NO_STATES; i++)
                        {
                            STATE_POLYGONS[i].setVisible(false);
                            STATE_POLYGONS[NO_STATES + i].setVisible(true);
                            STATE_POLYGONS[2 * NO_STATES + i] = new Polygon();
                            STATE_POLYGONS[2 * NO_STATES + i].getPoints().addAll(STATE_POLYGONS[i].getPoints());
                            makeStatePolygon(i, STATE_POLYGON_DRAGGABLE);
                            STATE_POLYGONS[2 * NO_STATES + i].setVisible(true);
                            makeDraggable(STATE_POLYGONS[2 * NO_STATES + i]);
                            pane.getChildren().clear();
                            pane.getChildren().addAll(STATE_POLYGONS);
                        }
                        intStateBeingDragged = -1;
                        intStateCount = -1;
                        lblStatus.setText("Click on Shuffle to rearrange: Beginner (move only) or Advanced (rotate and move)");
                    }
                }
            }
        });
        btnShuffle = new Button("Shuffle");
        btnShuffle.setVisible(false);
        btnShuffle.setOnAction(new EventHandler<ActionEvent>()
        {
            @Override
            public void handle(ActionEvent event)
            {
                for (int i = 0; i < NO_STATES; i++)
                {
                    STATE_POLYGONS[i].setVisible(false);
                    STATE_POLYGONS[2 * NO_STATES + i] = new Polygon();
                    //Create the random position (dx, dy) to move the state polygon.
                    double dx = Math.random() * VIEW_WIDTH / 2;
                    double dy = Math.random() * VIEW_HEIGHT / 2;
                    double xp;
                    double yp;
                    if (blnBeginnerMode)
                    {
                        //For the beginner mode, only move
                        for (int j = 0; j < (STATE_POLYGONS[i].getPoints().size() + 1) / 2; j++)
                        {
                            //For the polygon, points are arranged as x0, y0, x1, y1, ...
                            xp = STATE_POLYGONS[i].getPoints().get(2 * j) + dx;
                            yp = STATE_POLYGONS[i].getPoints().get(2 * j + 1) + dy;
                            STATE_POLYGONS[2 * NO_STATES + i].getPoints().add(xp);
                            STATE_POLYGONS[2 * NO_STATES + i].getPoints().add(yp);
                        }
                    } else {
                        //For the advanced mode, rotate and move.
                        double angle = Math.random() * 360;
                        double px = pane.getLayoutBounds().getWidth() / 2;
                        double py = pane.getLayoutBounds().getHeight() / 2;
                        for (int j = 0; j < (STATE_POLYGONS[i].getPoints().size() + 1) / 2; j++)
                        {
                            xp = STATE_POLYGONS[i].getPoints().get(2 * j);
                            yp = STATE_POLYGONS[i].getPoints().get(2 * j + 1);
                            //perform the rotation: rotate point (xp, yp) with random angle at pivot point (px, py).
                            double[] r = rotate(xp, yp, angle, px, py);
                            STATE_POLYGONS[2 * NO_STATES + i].getPoints().add(r[0] + dx);
                            STATE_POLYGONS[2 * NO_STATES + i].getPoints().add(r[1] + dy);
                        }
                    }
                    makeStatePolygon(i, STATE_POLYGON_DRAGGABLE);
                    //We need to check if the new state polygon is inside our window. Otherwise, we have to remake it.
                    if (!pane.intersects(STATE_POLYGONS[2 * NO_STATES + i].getBoundsInLocal()))
                    {
                        double px = (pane.getBoundsInLocal().getMinX() - STATE_POLYGONS[2 * NO_STATES + i].getBoundsInLocal().getMinX() ) / 2;
                        double py = (pane.getBoundsInLocal().getMinY() - STATE_POLYGONS[2 * NO_STATES + i].getBoundsInLocal().getMinY() ) / 2;
                        Object[] pts = STATE_POLYGONS[2 * NO_STATES + i].getPoints().toArray();
                        STATE_POLYGONS[2 * NO_STATES + i] = new Polygon();
                        for (int j = 0; j < (pts.length + 1) / 2; j++)
                        {
                            STATE_POLYGONS[2 * NO_STATES + i].getPoints().add(Double.valueOf(pts[2 * j].toString()) + px);
                            STATE_POLYGONS[2 * NO_STATES + i].getPoints().add(Double.valueOf(pts[2 * j + 1].toString()) + py);
                        }
                        makeStatePolygon(i, STATE_POLYGON_DRAGGABLE);
                        if (!pane.intersects(STATE_POLYGONS[2 * NO_STATES + i].getBoundsInLocal()))
                        {
                            System.out.println(i + ", " + STATE_NAMES[i] + ": still outside");
                            System.out.println(STATE_POLYGONS[2 * NO_STATES + i].getPoints());
                        }
                    }
                    STATE_POLYGONS[2 * NO_STATES + i].setVisible(true);
                    makeDraggable(STATE_POLYGONS[2 * NO_STATES + i]);
                    pane.getChildren().clear();
                    pane.getChildren().addAll(STATE_POLYGONS);
                }
                intStateBeingDragged = -1;
                intStateCount = -1;
                lblStatus.setText("");
            }
        });
        final ToggleGroup tg2 = new ToggleGroup();
        rbBeginner = new RadioButton(MODE_BEGINNER);
        rbBeginner.setToggleGroup(tg2);
        rbBeginner.setUserData(MODE_BEGINNER);
        rbBeginner.setPadding(new Insets(0, 0, 0, 50));
        rbBeginner.setSelected(true);
        rbBeginner.setVisible(false);
        blnBeginnerMode = true;
        rbAdvanced = new RadioButton(MODE_ADVANCED);
        rbAdvanced.setUserData(MODE_ADVANCED);
        rbAdvanced.setToggleGroup(tg2);
        rbAdvanced.setVisible(false);
        //rbAdvanced.setDisable(true);
        tg2.selectedToggleProperty().addListener(new ChangeListener<Toggle>()
        {
            @Override
            public void changed(ObservableValue<? extends Toggle> ov, Toggle old_toggle, Toggle new_toggle)
            {
                if (tg2.getSelectedToggle() != null)
                {
                    blnBeginnerMode = tg2.getSelectedToggle().getUserData().equals(MODE_BEGINNER);
                    btnShuffle.fire();
                }
            }
        });
        topbar.setAlignment(Pos.CENTER);
        configureBorder(topbar);
        topbar.getChildren().addAll(btnInformation, btnJigsawPuzzle, btnShuffle, rbBeginner, rbAdvanced);
        return topbar;
    }

    //Rotate point (xp, yp) with given angle at pivot point (0, 0)
    private double[] rotate(double xp, double yp, double angle)
    {
        double[] r = new double[2];
        r[0] = xp * Math.cos(angle) - yp * Math.sin(angle);
        r[1] = xp * Math.sin(angle) + yp * Math.cos(angle);
        return r;
    }

    //Rotate point (xp, yp) with given angle at pivot point (px, py)
    private double[] rotate(double xp, double yp, double angle, double px, double py)
    {
        double[] r = new double[2];
        r[0] = (xp - px) * Math.cos(angle) - (yp - py) * Math.sin(angle) + px;
        r[1] = (xp - px) * Math.sin(angle) + (yp - py) * Math.cos(angle) + py;
        return r;
    }

    private void createWiki(Stage stage)
    {
        wikiStage = new Stage();
        wikiStage.setTitle("Wikipedia");
        wikiStage.setWidth(800);
        wikiStage.setHeight(600);
        wikiStage.setX((stage.getX() - 200) > 0 ? (stage.getX() - 200) : 100);
        wikiStage.setY((stage.getY() - 100) > 0 ? (stage.getY() - 100) : 100);
        Scene scene = new Scene(new Group());
        VBox root = new VBox();
        final WebView browser = new WebView();
        wikiWE = browser.getEngine();
        root.getChildren().addAll(browser);
        scene.setRoot(root);
        wikiStage.setScene(scene);
    }

    private void createMIDIPlayer()
    {
        if (midiPlayer == null)
        {
            try
            {
                midiPlayer = MidiSystem.getSequencer();
            } catch (MidiUnavailableException e) {}
        }
    }

    private void playMIDI(String strSoundFile)
    {
        try
        {
            Sequence song = MidiSystem.getSequence(USStatesStats.class.getResourceAsStream(strSoundFile));
            midiPlayer.open();
            midiPlayer.setSequence(song);
            midiPlayer.setLoopCount(Sequencer.LOOP_CONTINUOUSLY);
            midiPlayer.start();
        } catch (Exception e) {e.printStackTrace();}
    }

    private void stopMIDI()
    {
        try
        {
            midiPlayer.stop();
            midiPlayer.close();
        } catch (Exception e) {}
    }

    private void loadMapXMLFile()
    {
        try
        {
            int intStates = 0;
            int intPoints = 0;
            Double[] xyp = new Double[400];
            XMLInputFactory xmlif = XMLInputFactory.newInstance();
            xmlif.setProperty(XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, Boolean.TRUE);
            xmlif.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, Boolean.FALSE);
            xmlif.setProperty(XMLInputFactory.IS_COALESCING, Boolean.FALSE);
            XMLStreamReader xmlr = xmlif.createXMLStreamReader(USStatesStats.class.getResourceAsStream(USSTATESMAPFILE));
            String elementName;
            String strName;
            String strColor;
            String strLat;
            String strLng;
            while (xmlr.hasNext())
            {
                int event = xmlr.next();
                if (event == XMLStreamConstants.START_ELEMENT)
                {
                    elementName = xmlr.getLocalName();
                    switch (elementName)
                    {
                        case "states":
                            xmlr.next();
                            break;
                        case "state":
                            strName = xmlr.getAttributeValue(0);
                            strColor = xmlr.getAttributeValue(1);
                            STATE_NAMES[intStates] = strName;
                            STATE_COLORS[intStates] = strColor;
                            STATE_POLYGONS[intStates] = new Polygon();
                            STATE_POLYGONS[NO_STATES + intStates] = new Polygon();
                            STATE_POLYGONS[2 * NO_STATES + intStates] = new Polygon();
                            intPoints = 0;
                            intStates++;
                            xmlr.next();
                            break;
                        case "point":
                            strLat = xmlr.getAttributeValue(0);
                            strLng = xmlr.getAttributeValue(1);
                            //System.out.println(intStates + ", " + intPoints + ", " + strName + ", " + strColor + ", " + strLat + ", " + strLng);
                            //We need to translate state boundary coordinates in latitudes and longitudes into pixles on screen (do it once).
                            xyp[intPoints * 2] = (Double.valueOf(strLng) + 140d) * 15d;
                            xyp[intPoints * 2 + 1] = 1100d - Double.valueOf(strLat) * 18d;
                            intPoints++;
                            xmlr.next();
                            break;
                    }
                }
                if (event == XMLStreamConstants.END_ELEMENT)
                {
                    switch (xmlr.getLocalName())
                    {
                        case "state":
                            Double[] ps = new Double[intPoints * 2];
                            System.arraycopy(xyp, 0, ps, 0, intPoints * 2);
                            //System.out.println(intStates + "," + STATE_NAMES[intStates - 1] + ", " + intPoints);
                            STATE_POLYGONS[intStates - 1].getPoints().addAll(ps);
                            STATE_POLYGONS[NO_STATES + intStates - 1].getPoints().addAll(ps);
                            STATE_POLYGONS[2 * NO_STATES + intStates - 1].getPoints().addAll(ps);
                            xmlr.next();
                            break;
                    }
                }
            }
            xmlr.close();
        } catch (Exception e) {e.printStackTrace();}
    }

    private void createAllStatePolygons()
    {
        for (int i = 0; i < NO_STATES; i++)
        {
            makeStatePolygon(i, STATE_POLYGON_INFO);
            STATE_POLYGONS[i].setVisible(true);
            makeStatePolygon(i, STATE_POLYGON_OUTLINE);
            STATE_POLYGONS[NO_STATES + i].setVisible(false);
            makeStatePolygon(i, STATE_POLYGON_DRAGGABLE);
            STATE_POLYGONS[2 * NO_STATES + i].setVisible(false);
            makeDraggable(STATE_POLYGONS[2 * NO_STATES + i]);
        }
    }

    private void makeStatePolygon(int i, int sptype)
    {
        switch (sptype)
        {
            case STATE_POLYGON_INFO:
                STATE_POLYGONS[i].setFill(Color.web(STATE_COLORS[i]));
                STATE_POLYGONS[i].setStrokeWidth(2);
                STATE_POLYGONS[i].setStroke(Color.GREY);
                STATE_POLYGONS[i].setId(STATE_NAMES[i]);
                Tooltip t1 = new Tooltip(STATE_NAMES[i]);
                Tooltip.install(STATE_POLYGONS[i], t1);
                break;
            case STATE_POLYGON_OUTLINE:
                int j = NO_STATES + i;
                STATE_POLYGONS[j].setFill(Color.TRANSPARENT);
                STATE_POLYGONS[j].getStrokeDashArray().addAll(5d, 15d);
                STATE_POLYGONS[j].setStrokeWidth(2);
                STATE_POLYGONS[j].setStroke(Color.LIGHTPINK);
                STATE_POLYGONS[j].setId(STATE_NAMES[i]);
                Tooltip t2 = new Tooltip(STATE_NAMES[i]);
                Tooltip.install(STATE_POLYGONS[j], t2);
                break;
            case STATE_POLYGON_DRAGGABLE:
                int k = 2 * NO_STATES + i;
                STATE_POLYGONS[k].setFill(Color.web(STATE_COLORS[i]));
                STATE_POLYGONS[k].setStrokeWidth(2);
                STATE_POLYGONS[k].setStroke(Color.RED);
                STATE_POLYGONS[k].setId(STATE_NAMES[i]);
                Tooltip t3 = new Tooltip(STATE_NAMES[i]);
                Tooltip.install(STATE_POLYGONS[k], t3);
                break;
            default:
                break;
        }
    }

    private static void configureBorder(final Region region)
    {
        region.setStyle("-fx-background-color: lightgrey;"
                + "-fx-border-color: black;"
                + "-fx-border-width: 1;"
                + "-fx-border-radius: 3;"
                + "-fx-padding: 3;");
    }

    private static void configureText(final Text text)
    {
        text.setStyle("-fx-font: 50px Tahoma;"
            + "-fx-fill: linear-gradient(from 0% 0% to 100% 200%, repeat, aqua 0%, red 50%);"
            + "-fx-stroke: black;"
            + "-fx-stroke-width: 1;");
    }

    private void makeDraggable(final Polygon p)
    {
        final DragContext dragContext = new DragContext();
        p.addEventFilter(MouseEvent.MOUSE_PRESSED, new EventHandler<MouseEvent>()
        {
            @Override
            public void handle(final MouseEvent mouseEvent)
            {
                //Remember initial mouse cursor coordinates and polygon position.
                dragContext.mouseAnchorX = mouseEvent.getX();
                dragContext.mouseAnchorY = mouseEvent.getY();
                dragContext.initialTranslateX = p.getTranslateX();
                dragContext.initialTranslateY = p.getTranslateY();
            }
        });
        p.addEventFilter(MouseEvent.MOUSE_DRAGGED, new EventHandler<MouseEvent>()
        {
            @Override
            public void handle(final MouseEvent mouseEvent)
            {
                //Shift polygon from its initial position by delta calculated from mouse cursor movement.
                p.setTranslateX(dragContext.initialTranslateX + mouseEvent.getX() - dragContext.mouseAnchorX);
                p.setTranslateY(dragContext.initialTranslateY + mouseEvent.getY() - dragContext.mouseAnchorY);
                intStateBeingDragged = -1;
                for (int i = 0; i < NO_STATES; i++)
                {
                    if (p.getId().equals(STATE_POLYGONS[NO_STATES + i].getId()))
                    {
                        intStateBeingDragged = i;
                        break;
                    }
                }
            }
        });
    }

    private void showProgress(Pane pane)
    {
        boolean blnShowProgress = false;
        boolean blnFinal = false;
        String strText = "";
        switch (intStateCount + 1)
        {
            case 10:
                blnShowProgress = true;
                strText = "Good job!";
                break;
            case 20:
                blnShowProgress = true;
                strText = "Great job!";
                break;
            case 30:
                blnShowProgress = true;
                strText = "Way to go!";
                break;
            case 40:
                blnShowProgress = true;
                strText = "Almost there!";
                break;
            case NO_STATES:
                blnShowProgress = true;
                strText = "Congradulations!";
                blnFinal = true;
                break;
            default:
                break;
        }

        if (blnShowProgress)
        {
            Text text = new Text(pane.getWidth() / 3 + 25, pane.getHeight() / 2, strText);
            configureText(text);
            ScaleTransition st = new ScaleTransition(Duration.millis(1000), text);
            st.setOnFinished(new EventHandler<ActionEvent>()
            {
                @Override
                public void handle(ActionEvent event)
                {
                    pane.getChildren().remove(text);
                }
            });
            if (blnFinal)
            {
                st.setByX(1.5);
                st.setByY(1.5);
                st.setCycleCount(8);
                showColorfulCircles(pane);
            } else {
                st.setByX(1.2);
                st.setByY(1.2);
                st.setCycleCount(4);
            }
            st.setAutoReverse(true);
            st.play();
            pane.getChildren().add(text);

        }
    }

    private void showColorfulCircles(Pane pane)
    {
        Group circles = new Group();
        for (int i = 0; i < 30; i++)
        {
            Circle circle = new Circle(50, Color.web("darkblue", 0.5));
            circle.setStrokeType(StrokeType.OUTSIDE);
            circle.setStroke(Color.web("gray", 0.25));
            circle.setStrokeWidth(2);
            circles.getChildren().add(circle);
        }
        Rectangle colors = new Rectangle(pane.getWidth(), pane.getHeight(), new LinearGradient(0f, 1f, 1f, 0f, true, CycleMethod.NO_CYCLE, new Stop[]
        {
            new Stop(0, Color.web("#f8bd55")),
            new Stop(0.14, Color.web("#c0fe56")),
            new Stop(0.28, Color.web("#5dfbc1")),
            new Stop(0.43, Color.web("#64c2f8")),
            new Stop(0.57, Color.web("#be4af7")),
            new Stop(0.71, Color.web("#ed5fc2")),
            new Stop(0.85, Color.web("#ef504c")),
            new Stop(1, Color.web("#f2660f")),
        }));
        colors.widthProperty().bind(pane.widthProperty());
        colors.heightProperty().bind(pane.heightProperty());
        Group blendModeGroup = new Group(new Group(new Rectangle(pane.getWidth(), pane.getHeight(), Color.TRANSPARENT), circles), colors);
        colors.setBlendMode(BlendMode.OVERLAY);
        pane.getChildren().add(blendModeGroup);
        circles.setEffect(new BoxBlur(10, 10, 3));
        Timeline timeline = new Timeline();
        for (Node circle : circles.getChildren())
        {
            timeline.getKeyFrames().addAll(
                new KeyFrame(Duration.ZERO, // set start position at 0
                new KeyValue(circle.translateXProperty(), random() * pane.getWidth()),
                new KeyValue(circle.translateYProperty(), random() * pane.getHeight())),
                new KeyFrame(new Duration(8000), // set end position at 8s
                new KeyValue(circle.translateXProperty(), random() * pane.getWidth()),
                new KeyValue(circle.translateYProperty(), random() * pane.getHeight())));
        }
        // play 8s of animation
        timeline.play();
        timeline.setOnFinished(new EventHandler<ActionEvent>()
        {
            @Override
            public void handle(ActionEvent event)
            {
                pane.getChildren().remove(blendModeGroup);
            }
        });
    }

    public static void main(String[] args) {launch(args);}

    private static final class DragContext
    {
        public double mouseAnchorX;
        public double mouseAnchorY;
        public double initialTranslateX;
        public double initialTranslateY;
    }
}
