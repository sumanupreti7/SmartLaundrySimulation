package laundromat;

import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;

/** Cream-and-sage operations view. Swing mutations happen exclusively on the EDT. */
public final class LaundryGui {
    private static final Color BG = new Color(0xF7F5EF), PAPER = new Color(0xFFFEFA);
    private static final Color INK = new Color(0x25322D), MUTED = new Color(0x727B70);
    private static final Color LINE = new Color(0xDDDCD3), SAGE = new Color(0x5C755B);
    private static final Color BLUE = new Color(0x3989C9), AMBER = new Color(0xCF963D);
    private static final Color GREEN = new Color(0x36A66C), RED = new Color(0xD25B53);
    private static final java.awt.image.BufferedImage APPLIANCE = loadAppliance();
    private static java.awt.image.BufferedImage loadAppliance() {
        try (java.io.InputStream stream = LaundryGui.class.getResourceAsStream("/assets/appliance.png")) {
            if (stream != null) return javax.imageio.ImageIO.read(stream);
        } catch (java.io.IOException ignored) { }
        try { return javax.imageio.ImageIO.read(new java.io.File("assets/appliance.png")); }
        catch (java.io.IOException ignored) { return null; } // Java2D fallback still works.
    }
    private JFrame frame;
    private final JButton startButton = button("Start simulation", SAGE, Color.WHITE);
    private final JButton stopButton = button("Stop", PAPER, INK);
    private final JComboBox<String> scenario = new JComboBox<>(new String[]{"Normal", "Congested"});
    private final JLabel clock = label("00:00", 23, true, INK);
    private final JLabel status = label("Ready", 12, false, MUTED);
    private final JLabel served = label("0 / 50", 22, true, INK);
    private final JLabel activeWashers = label("0 / 6", 22, true, INK);
    private final JLabel activeDryers = label("0 / 4", 22, true, INK);
    private final JLabel average = label("0.0 s", 22, true, INK);
    private final JLabel washQueue = label("0", 17, true, INK);
    private final JLabel dryQueue = label("0", 17, true, INK);
    private final JLabel payQueue = label("0", 17, true, INK);
    private final JLabel ownerState = label("Not called", 13, true, SAGE);
    private final JLabel peaks = label("Peak washers 0 / 6   |   Peak dryers 0 / 4", 12, false, MUTED);
    private final JLabel failures = label("Wash failures 0   |   Payment failures 0", 12, false, MUTED);
    private final JLabel manual = label("Manual payments 0", 12, false, MUTED);
    private final JTextArea eventLog = new JTextArea();
    private final DefaultTableModel activityModel = new DefaultTableModel(new Object[]{"Customer", "Stage", "State"}, 0) {
        @Override public boolean isCellEditable(int row, int col) { return false; }
    };
    private final JTable activityTable = new JTable(activityModel);
    private final FloorView floor = new FloorView();
    private final javax.swing.Timer motionTimer = new javax.swing.Timer(33, e -> floor.animate());
    private final javax.swing.Timer refreshTimer = new javax.swing.Timer(100, e -> refresh());
    private Statistics statistics;
    private LaundryFacility facility;
    private CustomerGenerator generator;
    private Thread arrivalThread;
    private boolean running, stopping;
    private int session;
    private long startNanos, endNanos;
    private String lastStatus = "Ready";

    public static void open(String autoStart) {
        SwingUtilities.invokeLater(() -> {
            LaundryGui gui = new LaundryGui();
            gui.build();
            if ("normal".equals(autoStart)) gui.start(false);
            if ("congested".equals(autoStart)) gui.start(true);
        });
    }

    private void build() {
        frame = new JFrame("Smart Laundry Simulation");
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.setContentPane(content());
        frame.setMinimumSize(new Dimension(1080, 740));
        frame.setSize(1380, 900);
        frame.setLocationRelativeTo(null);
        frame.addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { stop(); refreshTimer.stop(); motionTimer.stop(); }
        });
        frame.setVisible(true);
        refreshTimer.start();
        motionTimer.start();
    }

    private JPanel content() {
        JPanel root = panel(new BorderLayout(12, 12));
        root.setBorder(new EmptyBorder(14, 16, 14, 16));
        JPanel top = panel(new BorderLayout(0, 12));
        JPanel toolbar = surface(new BorderLayout(18, 0));
        JPanel left = panel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        left.setOpaque(false);
        left.add(label("Simulation mode", 13, false, INK));
        scenario.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        scenario.setPreferredSize(new Dimension(145, 34));
        left.add(scenario);
        left.add(status);
        JPanel right = panel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        right.setOpaque(false);
        right.add(startButton); right.add(stopButton); right.add(clock);
        toolbar.add(left, BorderLayout.WEST); toolbar.add(right, BorderLayout.EAST);
        top.add(toolbar, BorderLayout.NORTH);
        JPanel metrics = surface(new GridLayout(1, 4, 12, 0));
        metrics.add(metric("Customers served", served));
        metrics.add(metric("Washers in use", activeWashers));
        metrics.add(metric("Dryers in use", activeDryers));
        metrics.add(metric("Average visit", average));
        top.add(metrics, BorderLayout.CENTER);
        root.add(top, BorderLayout.NORTH);

        JPanel middle = panel(new BorderLayout(12, 0));
        JPanel facilityPanel = surface(new BorderLayout(0, 8));
        JPanel floorTitle = panel(new BorderLayout()); floorTitle.setOpaque(false);
        floorTitle.add(label("Laundromat floor", 18, true, INK), BorderLayout.WEST);
        floorTitle.add(label("Entry  /  Wash  /  Dry  /  Pay  /  Exit", 12, false, MUTED), BorderLayout.EAST);
        facilityPanel.add(floorTitle, BorderLayout.NORTH);
        facilityPanel.add(floor, BorderLayout.CENTER);
        middle.add(facilityPanel, BorderLayout.CENTER);

        JPanel sidebar = panel(new BorderLayout(0, 12));
        sidebar.setPreferredSize(new Dimension(310, 0));
        JPanel activity = surface(new BorderLayout(0, 10));
        activity.add(label("Live customer activity", 18, true, INK), BorderLayout.NORTH);
        activityTable.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        activityTable.setRowHeight(30);
        activityTable.setBackground(PAPER);
        activityTable.setForeground(INK);
        activityTable.setGridColor(LINE);
        activityTable.setSelectionBackground(new Color(0xE6EDE2));
        activityTable.setSelectionForeground(INK);
        activityTable.setShowVerticalLines(false);
        activityTable.setFillsViewportHeight(true);
        activityTable.getTableHeader().setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        activityTable.getTableHeader().setBackground(new Color(0xEFEFE7));
        activityTable.getTableHeader().setForeground(INK);
        activityTable.getTableHeader().setReorderingAllowed(false);
        activityTable.getColumnModel().getColumn(2).setCellRenderer(new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable table, Object value,
                    boolean selected, boolean focus, int row, int column) {
                super.getTableCellRendererComponent(table, value, selected, focus, row, column);
                String state = String.valueOf(value);
                if (!selected) setForeground("Active".equals(state) ? GREEN : "Waiting".equals(state)
                        || "Retrying".equals(state) ? AMBER : "Cancelled".equals(state) ? RED : MUTED);
                return this;
            }
        });
        JScrollPane activityScroll = new JScrollPane(activityTable);
        activityScroll.setBorder(BorderFactory.createLineBorder(LINE));
        activity.add(activityScroll, BorderLayout.CENTER);
        sidebar.add(activity, BorderLayout.CENTER);
        JPanel queues = surface(new GridLayout(0, 1, 0, 10));
        queues.add(label("Waiting queues", 18, true, INK));
        queues.add(pair("Washer queue", washQueue));
        queues.add(pair("Dryer queue", dryQueue));
        queues.add(pair("Payment queue", payQueue));
        queues.add(pair("Owner status", ownerState));
        queues.add(label("Owner is called at 30 waiting customers", 11, false, MUTED));
        sidebar.add(queues, BorderLayout.SOUTH);
        middle.add(sidebar, BorderLayout.EAST);
        root.add(middle, BorderLayout.CENTER);

        JPanel bottom = panel(new BorderLayout(0, 10));
        JPanel events = surface(new BorderLayout(0, 8));
        JPanel logHeader = panel(new BorderLayout()); logHeader.setOpaque(false);
        logHeader.add(label("Event log  /  live", 15, true, INK), BorderLayout.WEST);
        logHeader.add(label("Each event includes its actor and thread", 11, false, MUTED), BorderLayout.EAST);
        events.add(logHeader, BorderLayout.NORTH);
        eventLog.setEditable(false);
        eventLog.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        eventLog.setBackground(PAPER); eventLog.setForeground(INK);
        eventLog.setBorder(new EmptyBorder(5, 8, 5, 8));
        JScrollPane logScroll = new JScrollPane(eventLog);
        logScroll.setPreferredSize(new Dimension(100, 102));
        logScroll.setBorder(BorderFactory.createLineBorder(LINE));
        events.add(logScroll, BorderLayout.CENTER);
        bottom.add(events, BorderLayout.CENTER);
        JPanel footer = panel(new BorderLayout(10, 0));
        footer.add(label("Available   /   Running   /   Failed", 12, false, SAGE), BorderLayout.WEST);
        JPanel footerStats = panel(new FlowLayout(FlowLayout.RIGHT, 16, 0));
        footerStats.add(peaks); footerStats.add(failures); footerStats.add(manual);
        footer.add(footerStats, BorderLayout.CENTER);
        bottom.add(footer, BorderLayout.SOUTH);
        root.add(bottom, BorderLayout.SOUTH);
        stopButton.setEnabled(false);
        startButton.addActionListener(e -> start(scenario.getSelectedIndex() == 1));
        stopButton.addActionListener(e -> stop());
        return root;
    }

    private void start(boolean congested) {
        if (running) return;
        int run = ++session;
        running = true; stopping = false;
        floor.reset();
        scenario.setSelectedIndex(congested ? 1 : 0);
        scenario.setEnabled(false); startButton.setEnabled(false); stopButton.setEnabled(true);
        lastStatus = congested ? "Congested run" : "Normal run";
        eventLog.setText(""); activityModel.setRowCount(0);
        Statistics stats = new Statistics();
        SimulationLogger logger = new SimulationLogger(System.out, line -> append(run, line));
        LaundryFacility fac = new LaundryFacility(stats, logger, congested);
        CustomerGenerator gen = new CustomerGenerator(fac, stats, logger);
        Thread arrivals = new Thread(gen, "Thread-CustomerGenerator");
        statistics = stats; facility = fac; generator = gen; arrivalThread = arrivals;
        startNanos = System.nanoTime(); endNanos = startNanos;
        arrivals.start();
        new Thread(() -> {
            try { arrivals.join(); gen.awaitAllCustomers(); }
            catch (InterruptedException e) {
                arrivals.interrupt(); gen.interruptAll();
                Thread.currentThread().interrupt();
            } finally { fac.close(); }
            try { fac.awaitOwner(); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            long finished = System.nanoTime();
            Main.printSummary(stats, fac, (finished - startNanos) / 1_000_000_000.0);
            SwingUtilities.invokeLater(() -> {
                if (run != session) return;
                running = false; endNanos = finished;
                boolean complete = stats.getTotalCustomersServed() == SimulationConfig.NUMBER_OF_CUSTOMERS;
                lastStatus = complete ? "Complete" : "Stopped / incomplete";
                scenario.setEnabled(true); startButton.setEnabled(true); stopButton.setEnabled(false);
                append(run, String.format(Locale.ROOT,
                        "SUMMARY | Served %d/50 | Average %.2f s | Peak washers %d/6 | Peak dryers %d/4 | %.2f s",
                        stats.getTotalCustomersServed(), stats.getAverageCustomerTimeSeconds(),
                        stats.getMaxWashers(), stats.getMaxDryers(), (finished - startNanos) / 1e9));
                refresh();
            });
        }, "Thread-Coordinator").start();
        refresh();
    }

    private void stop() {
        if (!running || stopping) return;
        stopping = true; lastStatus = "Stopping...";
        stopButton.setEnabled(false);
        // The generator's lifecycle lock prevents a new un-interrupted customer slipping past cancellation.
        arrivalThread.interrupt(); generator.interruptAll(); facility.close();
    }

    private void append(int run, String line) {
        SwingUtilities.invokeLater(() -> {
            if (run != session) return;
            eventLog.append(line + "\n");
            if (eventLog.getLineCount() > 1800) {
                try { eventLog.replaceRange("", 0, eventLog.getLineEndOffset(299)); }
                catch (javax.swing.text.BadLocationException ignored) { }
            }
            eventLog.setCaretPosition(eventLog.getDocument().getLength());
        });
    }

    private void refresh() {
        status.setText(lastStatus);
        if (facility == null) { floor.repaint(); return; }
        long elapsed = ((running ? System.nanoTime() : endNanos) - startNanos) / 1_000_000_000L;
        clock.setText(String.format(Locale.ROOT, "%02d:%02d", elapsed / 60, elapsed % 60));
        served.setText(statistics.getTotalCustomersServed() + " / 50");
        activeWashers.setText(statistics.getCurrentWashers() + " / 6");
        activeDryers.setText(statistics.getCurrentDryers() + " / 4");
        average.setText(String.format(Locale.ROOT, "%.1f s", statistics.getAverageCustomerTimeSeconds()));
        washQueue.setText("" + facility.getWaitingForWasher());
        dryQueue.setText("" + facility.getWaitingForDryer());
        payQueue.setText("" + facility.getWaitingForPayment());
        ownerState.setText(switch (facility.getOwnerState()) {
            case 1 -> "On the way"; case 2 -> "Manual counter open"; default -> "Not called";
        });
        peaks.setText("Peak washers " + statistics.getMaxWashers() + "/6  |  Dryers " + statistics.getMaxDryers() + "/4");
        failures.setText("Failures: wash " + statistics.getWashFailures() + " / pay " + statistics.getPaymentFailures());
        manual.setText("Manual payments " + facility.getManualPayments());
        List<Customer> customers = generator.getCustomers();
        for (Customer c : customers) {
            Customer.Activity activity = c.getActivity();
            int row = c.getId() - 1;
            if (row >= activityModel.getRowCount()) activityModel.addRow(new Object[]{"C-" + c.getId(), activity.stage(), activity.state()});
            else {
                if (!activity.stage().equals(activityModel.getValueAt(row, 1))) activityModel.setValueAt(activity.stage(), row, 1);
                if (!activity.state().equals(activityModel.getValueAt(row, 2))) activityModel.setValueAt(activity.state(), row, 2);
            }
        }
        floor.sync(customers);
        floor.repaint();
    }

    private final class FloorView extends JPanel {
        private final java.util.Map<Integer, Walker> walkers = new java.util.LinkedHashMap<>();
        private WalkMotion ownerMotion;
        private long lastFrame;
        private int washWaiting, dryWaiting, payWaiting;
        private final class Walker {
            final int id;
            final WalkMotion motion = new WalkMotion(503, 623, 500);
            boolean hidden, departing;
            String stage = "Entry";
            Walker(int id) { this.id = id; }
        }
        void reset() {
            walkers.clear(); ownerMotion = null; lastFrame = System.nanoTime();
            washWaiting = dryWaiting = payWaiting = 0;
        }
        void animate() {
            long now = System.nanoTime();
            double dt = lastFrame == 0 ? 0 : Math.min(.10, (now - lastFrame) / 1e9);
            lastFrame = now;
            walkers.values().forEach(w -> w.motion.advance(dt));
            // Departed figures disappear only after the visual exit journey finishes.
            walkers.values().removeIf(w -> w.departing && !w.motion.moving());
            if (ownerMotion != null) ownerMotion.advance(dt);
            repaint();
        }
        void sync(List<Customer> customers) {
            List<Customer> ordered = new ArrayList<>(customers);
            ordered.sort(java.util.Comparator.comparingLong(c -> c.getActivity().changedNanos()));
            washWaiting = dryWaiting = payWaiting = 0;
            for (Customer c : ordered) {
                Customer.Activity a = c.getActivity();
                boolean exited = "Exit".equals(a.stage());
                Walker w = walkers.get(c.getId());
                if (w == null && exited) continue; // Do not resurrect a completed departure on each refresh.
                if (w == null) { w = new Walker(c.getId()); walkers.put(c.getId(), w); }
                if (exited) {
                    if (!w.departing) { w.departing = true; w.motion.speed(500); w.motion.target(550, 632, true); }
                    continue;
                }
                boolean newStage = !w.stage.equals(a.stage());
                w.stage = a.stage(); w.hidden = false;
                double x=503, y=573;
                boolean acquired = false;
                if ("Active".equals(a.state())) {
                    if ("Washing".equals(a.stage())) {
                        for (int i=0;i<6;i++) if (facility.getWashers().get(i).getCurrentCustomerId()==c.getId()) {
                            x=84+i*87; y=330; acquired=true; break;
                        }
                    } else if ("Drying".equals(a.stage())) {
                        for (int i=0;i<4;i++) if (facility.getDryers().get(i).getCurrentCustomerId()==c.getId()) {
                            x=743+i*87; y=330; acquired=true; break;
                        }
                    } else if ("Payment".equals(a.stage())) {
                        if (facility.isCongestedScenario() && facility.getManualCustomer()==c.getId()) {
                            x=746; y=560; acquired=true;
                        } else if (!facility.isCongestedScenario()) {
                            for (int i=0;i<2;i++) if (facility.getKiosks().get(i).getCurrentCustomerId()==c.getId()) {
                                x=867+i*84; y=541; acquired=true; break;
                            }
                        }
                    }
                    // Resource publication can lag a state change by a few instructions.
                    if (!acquired) continue;
                }
                if (!acquired) {
                    if ("Washing".equals(a.stage())) {
                        int i=washWaiting++; x=123+(i%3)*86; y=434+(i/3)*82; w.hidden=i>=6;
                    } else if ("Drying".equals(a.stage())) {
                        int i=dryWaiting++; x=546+(i%2)*79; y=423+(i/2)*82; w.hidden=i>=4;
                    } else if ("Payment".equals(a.stage())) {
                        int i=payWaiting++; x=770+(i%3)*63; y=575+(i/3)*48; w.hidden=i>=6;
                    }
                }
                if (w.hidden) continue;
                w.motion.speed(acquired || newStage ? 500 : 220);
                w.motion.target(x, y, newStage || acquired);
            }
            if (facility.getOwnerState() > 0) {
                if (ownerMotion == null) {
                    ownerMotion = new WalkMotion(503, 625, 572.0 / SimulationConfig.OWNER_RESPONSE_TIME);
                    // A five-second visual entrance, matched to the owner's configured response.
                    ownerMotion.target(772, 470, true);
                }
                if (facility.getOwnerState() == 2) ownerMotion.speed(500);
            }
        }
        FloorView() { setBackground(PAPER); setPreferredSize(new Dimension(980, 570)); }
        @Override protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            double scale = Math.min(getWidth() / 1100.0, getHeight() / 650.0);
            g.translate((getWidth() - 1100 * scale) / 2, (getHeight() - 650 * scale) / 2);
            g.scale(scale, scale);
            room(g);
            for (int i = 0; i < 6; i++) {
                WashingMachine m = facility == null ? null : facility.getWashers().get(i);
                machine(g, 44 + i * 87, 137, "W" + (i + 1), BLUE,
                        m == null ? 0 : m.getCurrentCustomerId(), m != null && m.isBroken(),
                        m == null ? 0 : m.getProgress());
            }
            for (int i = 0; i < 4; i++) {
                Dryer d = facility == null ? null : facility.getDryers().get(i);
                machine(g, 703 + i * 87, 137, "D" + (i + 1), AMBER,
                        d == null ? 0 : d.getCurrentCustomerId(), false, d == null ? 0 : d.getProgress());
            }
            kiosk(g, 897, 455, "P1", facility == null ? null : facility.getKiosks().get(0));
            kiosk(g, 981, 455, "P2", facility == null ? null : facility.getKiosks().get(1));
            if (facility != null && facility.getOwnerState() > 0) {
                badge(g, 684, 430, facility.getOwnerState() == 1 ? "Owner on the way" : "Manual cash counter", SAGE);
                if (facility.getOwnerState() == 2) {
                    g.setColor(new Color(0xA18D6C)); g.fillRoundRect(712, 477, 110, 40, 8, 8);
                    g.setColor(new Color(0x73634C)); g.fillRect(720, 515, 8, 36); g.fillRect(806, 515, 8, 36);

                }
            }
            drawCustomers(g);
            if (ownerMotion != null) {
                person(g, (int)ownerMotion.x(), (int)ownerMotion.y(), 0, "Owner",
                        new Color(0x6C5D44), ownerMotion.moving());
            }
            g.dispose();
        }

        private void room(Graphics2D g) {
            g.setColor(new Color(0xE7E2D6));
            g.fill(new Polygon(new int[]{16, 76, 1038, 1084}, new int[]{94, 38, 38, 94}, 4));
            g.setColor(new Color(0xC1BBAE)); g.setStroke(new BasicStroke(7));
            g.draw(new Polygon(new int[]{16, 76, 1038, 1084, 1084, 16}, new int[]{94, 38, 38, 94, 616, 616}, 6));
            g.setPaint(new GradientPaint(0, 80, new Color(0xF3EBDE), 0, 285, new Color(0xDFD4C0)));
            g.fillRect(26, 94, 1048, 204);
            g.setColor(new Color(0xDFD3C0));
            g.fill(new Polygon(new int[]{26, 78, 78, 26}, new int[]{94, 128, 555, 612}, 4));
            g.fill(new Polygon(new int[]{1074, 1036, 1036, 1074}, new int[]{94, 128, 555, 612}, 4));
            g.setPaint(new GradientPaint(0, 282, new Color(0xF1E9DA), 0, 614, new Color(0xE5D9C5)));
            g.fill(new Polygon(new int[]{26, 1074, 1074, 26}, new int[]{282, 282, 612, 612}, 4));
            Shape roomClip = g.getClip();
            g.clip(new Polygon(new int[]{26, 1074, 1074, 26}, new int[]{282, 282, 612, 612}, 4));
            g.setColor(new Color(0xDACEBC)); g.setStroke(new BasicStroke(1));
            for (int y = 300; y < 616; y += 45) g.drawLine(26, y, 1074, y);
            for (int x = -600; x < 1800; x += 95) {
                int topX = 550 + (x - 550) / 2;
                g.drawLine(topX, 282, x, 612);
            }
            g.setClip(roomClip);
            g.setColor(new Color(0xD1C5B1)); g.fillRect(26, 280, 1048, 8);
            // Window and decorative bench are scenery; all appliances are live Java drawings.
            g.setColor(new Color(0xAFBCB3)); g.fillRect(32, 165, 12, 128);
            plant(g, 53, 432, 1.15); plant(g, 1060, 391, 1.0);
            plant(g, 594, 308, .72); plant(g, 688, 308, .72);
            g.setColor(new Color(0x9E835F)); g.fillRoundRect(610, 269, 63, 28, 4, 4);
            g.setColor(new Color(0x6C5B44)); g.fillRect(617, 294, 5, 19); g.fillRect(660, 294, 5, 19);
            g.setColor(new Color(0xF8F4EB)); g.fillRect(604, 115, 77, 104);
            g.setColor(new Color(0xB8AE9B)); g.drawRect(604, 115, 77, 104);
            text(g, "SMART", 642, 146, 14, SAGE, true, true);
            text(g, "LAUNDRY", 642, 171, 13, SAGE, true, true);
            text(g, "6 / 4 / 2", 642, 195, 12, MUTED, false, true);
            badge(g, 176, 351, "2. Washing", SAGE); badge(g, 785, 351, "3. Drying", SAGE);
            badge(g, 884, 413, "4. Payment", SAGE); badge(g, 476, 521, "1. Entry / Exit", SAGE);
            path(g, new int[]{490, 420, 250, 250}, new int[]{516, 478, 478, 309});
            path(g, new int[]{568, 648, 844, 844}, new int[]{331, 375, 375, 309});
            path(g, new int[]{899, 920, 934}, new int[]{340, 381, 434});
            g.setColor(new Color(0x7F8980)); g.fillRect(440, 583, 196, 14);
            g.setColor(new Color(0xD6E2DC)); g.fillRect(450, 597, 82, 35); g.fillRect(540, 597, 82, 35);
            g.setColor(new Color(0x7F8980)); g.drawRect(450, 597, 82, 35); g.drawRect(540, 597, 82, 35);
            plant(g, 407, 616, .9); plant(g, 677, 616, .9);
            text(g, "6 WASHERS", 297, 104, 13, SAGE, true, true);
            text(g, "4 DRYERS", 872, 104, 13, SAGE, true, true);
        }

        private void drawCustomers(Graphics2D g) {
            List<Walker> depthOrder = new ArrayList<>(walkers.values());
            depthOrder.sort(java.util.Comparator.comparingDouble(w -> w.motion.y()));
            for (Walker w : depthOrder) {
                if (!w.hidden) person(g, (int)w.motion.x(), (int)w.motion.y(), w.id,
                        w.departing ? "Exit" : "", shirt(w.id), w.motion.moving());
            }
            if (washWaiting > 6) badge(g, 126, 547, "+" + (washWaiting - 6) + " waiting", SAGE);
            if (dryWaiting > 4) badge(g, 499, 534, "+" + (dryWaiting - 4) + " waiting", SAGE);
            if (payWaiting > 6) badge(g, 754, 624, "+" + (payWaiting - 6) + " waiting", AMBER);
        }
    }

    private static void machine(Graphics2D g, int x, int y, String id, Color accent,
                                int customer, boolean broken, double progress) {
        Color state = broken ? RED : customer == 0 ? MUTED : GREEN;
        g.setColor(new Color(0, 0, 0, 28)); g.fillOval(x - 4, y + 129, 88, 19);
        if (APPLIANCE != null) {
            g.drawImage(APPLIANCE, x, y, x + 78, y + 140, 188, 45, 1040, 1246, null);
            g.setColor(accent); g.setStroke(new BasicStroke(2));
            g.drawOval(x + 9, y + 31, 60, 73);
            g.setColor(state); g.fillOval(x + 8, y + 13, 6, 6);
        } else {
        g.setPaint(new GradientPaint(x, y, Color.WHITE, x + 76, y, new Color(0xBABEBC)));
        g.fillRoundRect(x, y, 78, 140, 6, 6);
        g.setColor(new Color(0x8A918D)); g.setStroke(new BasicStroke(1)); g.drawRoundRect(x, y, 78, 140, 6, 6);
        g.setColor(new Color(0xE8E9E3)); g.fillRect(x + 3, y + 5, 72, 26);
        g.setColor(new Color(0x242C2A)); g.fillRoundRect(x + 39, y + 12, 28, 12, 2, 2);
        text(g, customer == 0 ? "READY" : broken ? "RESET" : "RUN", x + 53, y + 21, 6, broken ? RED : new Color(0x9CD3BF), true, true);
        g.setColor(state); g.fillOval(x + 9, y + 13, 8, 8);
        g.setColor(new Color(0xA5AAA6)); g.fillRect(x + 21, y + 13, 12, 6);
        g.setPaint(new GradientPaint(x + 8, y + 38, accent.brighter(), x + 64, y + 109, accent.darker()));
        g.fillOval(x + 8, y + 38, 62, 65);
        g.setColor(new Color(0xDEE2DF)); g.setStroke(new BasicStroke(2)); g.drawOval(x + 8, y + 38, 62, 65);
        g.setPaint(new RadialGradientPaint(new Point2D.Float(x + 35, y + 64), 30,
                new float[]{0, 1}, new Color[]{new Color(0x435657), new Color(0x121B1C)}));
        g.fillOval(x + 15, y + 45, 48, 51);
        }
        if (customer != 0) {
            Shape previous = g.getClip(); g.clip(new Ellipse2D.Double(x + 16, y + 46, 47, 49));
            AffineTransform transform = g.getTransform();
            g.rotate(broken ? 0 : progress * Math.PI * 10, x + 39, y + 70);
            g.setColor(new Color(0xB8D1DF)); g.fillRoundRect(x + 19, y + 57, 31, 17, 8, 8);
            g.setColor(new Color(0xE8E6D5)); g.fillRoundRect(x + 30, y + 72, 30, 16, 8, 8);
            g.setColor(new Color(0x879BAE)); g.fillRoundRect(x + 22, y + 79, 19, 19, 6, 6);
            g.setTransform(transform); g.setClip(previous);
        }
        g.setColor(new Color(255, 255, 255, 65)); g.fillArc(x + 18, y + 47, 44, 47, 50, 70);
        g.setColor(new Color(0xD0D4CF)); g.fillRoundRect(x + 10, y + 118, 58, 5, 4, 4);
        if (customer != 0) { g.setColor(broken ? RED : accent); g.fillRoundRect(x + 10, y + 118, (int)(58 * progress), 5, 4, 4); }
        badge(g, x + 12, y - 24, id, SAGE);
        text(g, customer == 0 ? "Available" : "C-" + customer + (broken ? " reset" : ""), x + 39, y + 156, 10, broken ? RED : INK, false, true);
    }

    private void kiosk(Graphics2D g, int x, int y, String id, PaymentKiosk kiosk) {
        boolean offline = facility != null && !facility.arePaymentKiosksInService();
        int customer = kiosk == null ? 0 : kiosk.getCurrentCustomerId();
        boolean failed = kiosk != null && kiosk.hasJustFailed();
        Color accent = offline || failed ? RED : customer > 0 ? GREEN : SAGE;
        g.setColor(new Color(0, 0, 0, 28)); g.fillOval(x - 6, y + 105, 75, 17);
        g.setColor(new Color(0xB8BFBA)); g.fill(new Polygon(new int[]{x+54,x+67,x+67,x+54}, new int[]{y,y+12,y+109,y+104},4));
        g.setPaint(new GradientPaint(x,y,new Color(0xF5F5EE),x+55,y+100,new Color(0xCED4CE)));
        g.fillRoundRect(x, y, 56, 109, 6, 6);
        g.setColor(new Color(0x6B7770)); g.drawRoundRect(x,y,56,109,6,6);
        g.setColor(new Color(0x293E37)); g.fillRoundRect(x+7,y+10,42,42,4,4);
        g.setColor(offline ? new Color(0x4F5551) : new Color(0xA3D3BD)); g.fillRect(x+12,y+15,32,31);
        text(g, offline ? "OFFLINE" : customer == 0 ? "READY" : "PAY", x+28,y+33,7,offline?RED:INK,true,true);
        g.setColor(accent); g.fillOval(x+8,y+60,6,6);
        g.setColor(new Color(0x4F5C54)); g.fillRect(x+25,y+60,19,4); g.fillRoundRect(x+15,y+80,26,10,2,2);
        badge(g,x+11,y-23,id,SAGE);
        text(g,offline?"Offline":customer==0?"Available":"C-"+customer,x+28,y+127,10,accent,false,true);
    }

    private static void person(Graphics2D g, int x, int y, int id, String title, Color shirt, boolean walking) {
        g.setColor(new Color(0,0,0,25));g.fillOval(x-17,y-4,38,10);
        double phase = System.nanoTime()/140_000_000.0 + id;
        double bob = walking ? Math.sin(phase) * 1.8 : 0;
        int stride = walking ? (int)(Math.sin(phase) * 4) : 0;
        AffineTransform before=g.getTransform();g.translate(0,bob);
        g.setColor(new Color(0x48514D));g.fillRoundRect(x-10,y-29+stride,9,27-stride,4,4);g.fillRoundRect(x+2,y-29-stride,9,27+stride,4,4);
        g.setColor(new Color(0x2C3531));g.fillRoundRect(x-12,y-6,12,6,3,3);g.fillRoundRect(x+1,y-6,13,6,3,3);
        g.setColor(shirt);g.fillRoundRect(x-13,y-58,28,35,9,9);
        g.setColor(new Color(0xD0AE8D));g.fillOval(x-10,y-81,20,23);
        g.setColor(new Color(0x423C32));g.fillArc(x-11,y-83,22,18,0,190);
        g.setColor(shirt.darker());g.setStroke(new BasicStroke(7,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));g.drawLine(x+11,y-50,x+24,y-36);
        g.setColor(new Color(0xDADCCB));g.fillRoundRect(x+14,y-42,26,18,4,4);
        g.setColor(new Color(0x7F8E81));g.setStroke(new BasicStroke(1));g.drawRoundRect(x+14,y-42,26,18,4,4);
        g.drawLine(x+17,y-37,x+37,y-37);g.drawLine(x+17,y-32,x+37,y-32);
        g.setTransform(before);
        text(g,id==0?title:"C-"+id,x,y+14,10,INK,true,true);
    }
    private static Color shirt(int id) { return new Color[]{SAGE,new Color(0xA57460),new Color(0x698798),new Color(0xB29863)}[id%4]; }
    private static void plant(Graphics2D g,int x,int y,double scale) {
        AffineTransform before=g.getTransform();g.translate(x,y);g.scale(scale,scale);
        g.setColor(new Color(0xB2A58A));g.fill(new Polygon(new int[]{-16,16,11,-11},new int[]{-24,-24,0,0},4));
        g.setColor(SAGE);g.setStroke(new BasicStroke(3));
        for(int i=0;i<7;i++){int lx=(i-3)*8,ly=-43-(i%3)*12;g.drawLine(0,-24,lx,ly);g.fillOval(lx-6,ly-12,14,22);}
        g.setTransform(before);
    }
    private static void path(Graphics2D g,int[] xs,int[] ys) {
        g.setColor(new Color(0x8D9B85));g.setStroke(new BasicStroke(1.8f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND,0,new float[]{7,7},0));
        g.drawPolyline(xs,ys,xs.length);int i=xs.length-1;double a=Math.atan2(ys[i]-ys[i-1],xs[i]-xs[i-1]);
        int x=xs[i],y=ys[i];g.fill(new Polygon(new int[]{x,(int)(x-10*Math.cos(a-.5)),(int)(x-10*Math.cos(a+.5))},new int[]{y,(int)(y-10*Math.sin(a-.5)),(int)(y-10*Math.sin(a+.5))},3));
    }
    private static void badge(Graphics2D g,int x,int y,String title,Color color) {
        g.setFont(new Font(Font.SANS_SERIF,Font.BOLD,11));int w=g.getFontMetrics().stringWidth(title)+18;
        g.setColor(new Color(0xF5F1E7));g.fillRoundRect(x,y,w,22,12,12);
        g.setColor(color);g.drawString(title,x+9,y+15);
    }
    private static void text(Graphics2D g,String value,int x,int y,int size,Color color,boolean bold,boolean centered) {
        g.setFont(new Font(Font.SANS_SERIF,bold?Font.BOLD:Font.PLAIN,size));g.setColor(color);
        g.drawString(value,centered?x-g.getFontMetrics().stringWidth(value)/2:x,y);
    }
    private static JPanel panel(LayoutManager layout) { JPanel p=new JPanel(layout);p.setBackground(BG);return p; }
    private static JPanel surface(LayoutManager layout) {
        JPanel p=new JPanel(layout);p.setBackground(PAPER);
        p.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(LINE),new EmptyBorder(12,14,12,14)));return p;
    }
    private static JLabel label(String text,int size,boolean bold,Color color) {
        JLabel l=new JLabel(text);l.setFont(new Font(Font.SANS_SERIF,bold?Font.BOLD:Font.PLAIN,size));l.setForeground(color);return l;
    }
    private static JPanel metric(String title,JLabel value) {
        JPanel p=new JPanel(new GridLayout(2,1,0,5));p.setOpaque(false);
        p.add(label(title,13,false,MUTED));p.add(value);return p;
    }
    private static JPanel pair(String title,JLabel value) {
        JPanel p=new JPanel(new BorderLayout(8,0));p.setOpaque(false);
        p.add(label(title,13,false,INK),BorderLayout.WEST);p.add(value,BorderLayout.EAST);return p;
    }
    private static JButton button(String text,Color bg,Color fg) {
        JButton b=new JButton(text);b.setUI(new javax.swing.plaf.basic.BasicButtonUI());
        b.setFont(new Font(Font.SANS_SERIF,Font.BOLD,13));b.setBackground(bg);b.setForeground(fg);
        b.setOpaque(true);b.setFocusPainted(false);b.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(LINE),new EmptyBorder(9,16,9,16)));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));return b;
    }
}

