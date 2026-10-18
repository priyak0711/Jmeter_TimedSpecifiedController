package com.example.jmeter.controller.gui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextField;

import org.apache.jmeter.control.gui.AbstractControllerGui;
import org.apache.jmeter.testelement.TestElement;

import com.example.jmeter.controller.TimedSpecifiedController;

/**
 * GUI for the Timed Specified Controller.
 */
public class TimedSpecifiedControllerGui extends AbstractControllerGui {

    private static final long serialVersionUID = 2L;

    private final JTextField intervalField = new JTextField("1", 8);
    private final JComboBox<String> unitBox = new JComboBox<>(new String[] {
            TimedSpecifiedController.UNIT_SECONDS, TimedSpecifiedController.UNIT_MINUTES });
    private final JRadioButton onceMode =
            new JRadioButton("Run once, when the interval has elapsed", true);
    private final JRadioButton repeatMode =
            new JRadioButton("Run repeatedly, every time the interval elapses");
    private final JCheckBox globalScope =
            new JCheckBox("Global schedule (one thread runs it per due time, not every thread)");

    public TimedSpecifiedControllerGui() {
        init();
    }

    private void init() {
        setLayout(new BorderLayout(0, 5));
        setBorder(makeBorder());
        add(makeTitlePanel(), BorderLayout.NORTH);

        // One column of rows, each only as tall as it needs to be.
        JPanel settings = new JPanel(new GridLayout(0, 1, 0, 0));
        settings.setBorder(BorderFactory.createTitledBorder("Timing"));

        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT));
        row.add(new JLabel("Interval (of test time):"));
        row.add(intervalField);
        row.add(unitBox);
        settings.add(row);

        ButtonGroup group = new ButtonGroup();
        group.add(onceMode);
        group.add(repeatMode);

        JPanel modeRow1 = new JPanel(new FlowLayout(FlowLayout.LEFT));
        modeRow1.add(onceMode);
        settings.add(modeRow1);

        JPanel modeRow2 = new JPanel(new FlowLayout(FlowLayout.LEFT));
        modeRow2.add(repeatMode);
        settings.add(modeRow2);

        JPanel scopeRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        scopeRow.add(globalScope);
        settings.add(scopeRow);

        // Keep the settings at their natural height, pinned to the top,
        // instead of letting them stretch to fill the whole window.
        JPanel top = new JPanel(new BorderLayout());
        top.add(settings, BorderLayout.NORTH);
        add(top, BorderLayout.CENTER);
    }

    @Override
    public String getLabelResource() {
        return "timed_specified_controller_title";
    }

    @Override
    public String getStaticLabel() {
        return "Timed Specified Controller";
    }

    @Override
    public TestElement createTestElement() {
        TimedSpecifiedController controller = new TimedSpecifiedController();
        modifyTestElement(controller);
        return controller;
    }

    @Override
    public void modifyTestElement(TestElement element) {
        configureTestElement(element);
        if (element instanceof TimedSpecifiedController) {
            TimedSpecifiedController c = (TimedSpecifiedController) element;
            double interval;
            try {
                interval = Double.parseDouble(intervalField.getText().trim());
            } catch (NumberFormatException e) {
                interval = 1.0;
            }
            c.setInterval(interval);
            c.setUnit(String.valueOf(unitBox.getSelectedItem()));
            c.setRepeat(repeatMode.isSelected());
            c.setGlobalScope(globalScope.isSelected());
        }
    }

    @Override
    public void configure(TestElement element) {
        super.configure(element);
        if (element instanceof TimedSpecifiedController) {
            TimedSpecifiedController c = (TimedSpecifiedController) element;
            intervalField.setText(String.valueOf(c.getInterval()));
            unitBox.setSelectedItem(c.getUnit());
            repeatMode.setSelected(c.isRepeat());
            onceMode.setSelected(!c.isRepeat());
            globalScope.setSelected(c.isGlobalScope());
        }
    }

    @Override
    public void clearGui() {
        super.clearGui();
        intervalField.setText("1");
        unitBox.setSelectedItem(TimedSpecifiedController.UNIT_MINUTES);
        onceMode.setSelected(true);
        globalScope.setSelected(false);
    }
}