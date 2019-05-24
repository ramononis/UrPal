package nl.utwente.ewi.fmt.uppaalSMC.urpal.ui;

import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.KeyEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import javax.swing.*;

import com.uppaal.engine.Problem;
import com.uppaal.model.system.symbolic.SymbolicTrace;
import com.uppaal.plugin.*;
import kotlin.Unit;
import nl.utwente.ewi.fmt.uppaalSMC.urpal.properties.SanityCheckResult;
import nl.utwente.ewi.fmt.uppaalSMC.urpal.util.ProblemWrapper;
import nl.utwente.ewi.fmt.uppaalSMC.urpal.util.SanityLog;
import nl.utwente.ewi.fmt.uppaalSMC.urpal.util.SanityLogRepository;
import nl.utwente.ewi.fmt.uppaalSMC.urpal.util.UppaalUtil;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.xtext.resource.XtextResourceSet;

import com.google.inject.Injector;
import com.uppaal.engine.EngineException;
import com.uppaal.model.core2.Document;
import com.uppaal.model.io2.XMLWriter;
import com.uppaal.model.system.UppaalSystem;
import com.uppaal.plugin.Repository.ChangeType;

import nl.utwente.ewi.fmt.uppaalSMC.parser.UppaalSMCStandaloneSetup;
import nl.utwente.ewi.fmt.uppaalSMC.NSTA;
import nl.utwente.ewi.fmt.uppaalSMC.urpal.properties.AbstractProperty;
import nl.utwente.ewi.fmt.uppaalSMC.urpal.properties.SanityCheck;

@SuppressWarnings("serial")
public class MainUI extends JPanel implements Plugin, PluginWorkspace, PropertyChangeListener {
    protected static final String SELECT = "com/uppaal/resource/images/selectedQuery.gif";
    protected static final String OKAY = "com/uppaal/resource/images/queryOkay.gif";
    protected static final String NOT_OKAY = "com/uppaal/resource/images/queryNotOkay.gif";
    protected static final String UNKNOWN = "com/uppaal/resource/images/queryUnknown.gif";
    protected static final String MAYBE_OKAY = "com/uppaal/resource/images/queryMaybeOkay.gif";
    protected static final String MAYBE_NOT_OKAY = "com/uppaal/resource/images/queryMaybeNotOkay.gif";

    private String getIconByOutcome(SanityCheckResult.Outcome outcome) {
        switch (outcome) {
            case TIMEOUT:
            case CANCELED:
            case EXCEPTION:
                return MAYBE_NOT_OKAY;
            case VIOLATED:
                return NOT_OKAY;
            case SATISFIED:
                return OKAY;
            default:
                return UNKNOWN;
        }
    }
    private ImageIcon getIcon(String resource) {
        return new ImageIcon(getClass().getClassLoader().getResource(resource));
    }
    private final Set<PropertyPanel> panels = new HashSet<>();
    private ResourceSet rs;
    private Repository<Document> docr;

    public static Repository<SymbolicTrace> getTracer() {
        return tracer;
    }

    private static Repository<SymbolicTrace> tracer;

    public static Repository<ArrayList<Problem>> getProblemr() {
        return problemr;
    }

    private static Repository<ArrayList<Problem>> problemr;

    public static Repository<UppaalSystem> getSystemr() {
        return systemr;
    }

    private static Repository<UppaalSystem> systemr;

    private static SanityLogRepository slr;

    public static SanityLogRepository getSlr() {
        return slr;
    }

    private boolean selected;
    private double zoom;

    private final PluginWorkspace[] workspaces = new PluginWorkspace[1];

    private NSTA load(Document d) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try {
            new XMLWriter(out).visitDocument(d);
            InputStream in = new ByteArrayInputStream(out.toByteArray());
            Resource resource = rs.createResource(URI.createURI("dummy:/" + System.currentTimeMillis() + ".xml"), null);
            resource.load(in, rs.getLoadOptions());

            return (NSTA) resource.getContents().get(0);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private class PropertyPanel extends JPanel {
        private final AbstractProperty property;
        private boolean enabled;
        private Component component;
        private ImageIcon icon;
        private JCheckBox checkBox;

        private void redoStuff() {
            Component c = component;
            if (c == null)
                c = this;
            while (c.getParent() != null) {
                c = c.getParent();
            }
            c.revalidate();
            c.repaint();
        }

        PropertyPanel(AbstractProperty p) {
            super();
            property = p;
            icon = getIcon(UNKNOWN);
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            setBorder(BorderFactory.createTitledBorder(property.getClass().getAnnotation(SanityCheck.class).name()));
//            JPanel panel = new JPanel();
            checkBox = new JCheckBox(property.getClass().getAnnotation(SanityCheck.class).name());
            checkBox.setIcon(UIManager.getIcon ("CheckBox.icon"));
            checkBox.setSelectedIcon(icon);
            checkBox.setSelected(enabled = true);
            checkBox.addItemListener(a -> {
                enabled = a.getStateChange() == ItemEvent.SELECTED;
                if (enabled) {
                    Thread.yield();
                    //doCheck(null, null, null);
                } else {
                    if (component != null)
                        remove(component);
                }
                redoStuff();
            });
            add(checkBox);
//            panel.add(new JLabel(icon));
//            panel.add(checkBox);
//            add(panel);
        }

        void check(NSTA nsta, Document doc, UppaalSystem sys) {
            checkBox.setSelectedIcon(getIcon(MAYBE_OKAY));
            if (nsta == null) {
                nsta = load(doc = docr.get());
                try {
                    sys = UppaalUtil.INSTANCE.compile(doc);
                } catch (EngineException | IOException e) {
                    e.printStackTrace();
                    return;
                }
            }
            property.check(nsta, doc, sys, pr -> {
                if (pr == null) {
                    docr.fire(ChangeType.valueOf("UPDATED"));
                    return Unit.INSTANCE;
                }
                checkBox.setSelectedIcon(getIcon(getIconByOutcome(pr.getOutcome())));
                slr.addToLog(pr);
                if (component != null)
                    remove(component);
                add(component = pr.toPanel());
                redoStuff();
                return Unit.INSTANCE;
            });
        }
    }

    public MainUI() {
    }

    @SuppressWarnings("unchecked")
    public MainUI(Registry r) {
        super();
        docr = r.getRepository("EditorDocument");
        tracer = r.getRepository("SymbolicTrace");
        problemr = r.getRepository("EditorProblems");
        r.publishRepository(slr = new SanityLogRepository());
        workspaces[0] = this;
        r.addListener(this);

        Injector injector = UppaalSMCStandaloneSetup.doSetup();
        rs = injector.getInstance(XtextResourceSet.class);

        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

        JPanel jp = new JPanel();
        jp.add(new JLabel("States to explore per check (<=0 for unlimited): "));
        JTextField nStates = new JFormattedTextField(NumberFormat.getIntegerInstance());
        nStates.setText("" + AbstractProperty.Companion.getSTATE_SPACE_SIZE());
        nStates.setPreferredSize(new Dimension(128, nStates.getPreferredSize().height));
        nStates.addPropertyChangeListener("value", evt -> {
            System.out.println(evt.getNewValue());
            AbstractProperty.Companion.setSTATE_SPACE_SIZE(Integer.parseInt(evt.getNewValue().toString()));
        });
        jp.add(nStates);
        add(jp);
        JButton runButton = new JButton("Run selected checks");
        runButton.addActionListener(e -> doCheck());
        add(runButton);
        for (AbstractProperty p : AbstractProperty.Companion.getProperties()) {
            PropertyPanel pp = new PropertyPanel(p);
            add(pp);
            panels.add(pp);
        }

        docr.addListener(this);
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(e -> {
            if (e.getKeyCode() == KeyEvent.VK_F6 && e.getID() == KeyEvent.KEY_PRESSED) {
                doCheck();
                return true;
            }
            return false;
        });
    }

    @Override
    public Icon getIcon() {
        return null;
    }

    @Override
    public String getTitleToolTip() {
        return "Detect commonly made errors";
    }

    @Override
    public Component getComponent() {
        return new JScrollPane(this);
    }

    @Override
    public int getDevelopmentIndex() {
        return 350;
    }

    @Override
    public boolean getCanZoom() {
        return false;
    }

    @Override
    public boolean getCanZoomToFit() {
        return false;
    }

    @Override
    public double getZoom() {
        return zoom;
    }

    @Override
    public void setZoom(double value) {
        zoom = value;
    }

    @Override
    public void zoomToFit() {
    }

    @Override
    public void zoomIn() {
    }

    @Override
    public void zoomOut() {
    }

    @Override
    public void setActive(boolean selected) {
        this.selected = selected;
    }

    private void doCheck() {
        if (panels.stream().anyMatch(p -> p.enabled)) {
            Document d = docr.get();
            NSTA nsta = load(d);
            UppaalSystem sys;
            try {
                sys = UppaalUtil.INSTANCE.compile(d);
            } catch (EngineException | IOException e) {
                e.printStackTrace();
                return;
            }
            ArrayList<Problem> problems = problemr.get();
            if (problems != null)
                problems.removeIf((it) -> it instanceof ProblemWrapper);
            new Thread(() -> panels.stream().filter(p -> p.enabled).forEach(p -> p.check(nsta, d, sys))).start();
        }
    }

    @Override
    public PluginWorkspace[] getWorkspaces() {
        return workspaces;
    }

    @Override
    public String getTitle() {
        return "Sanity Checker";
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        setActive(selected);
    }
}
