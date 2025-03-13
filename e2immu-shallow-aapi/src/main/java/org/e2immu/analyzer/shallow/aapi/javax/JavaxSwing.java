package org.e2immu.analyzer.shallow.aapi.javax;

import org.e2immu.annotation.Modified;

import javax.swing.table.TableCellRenderer;
import java.awt.event.ActionListener;

public class JavaxSwing {
    public static final String PACKAGE_NAME = "javax.swing";

    interface AbstractButton$ {
        @Modified
        void addActionListener(ActionListener l);
    }

    interface JTable$ {
        @Modified
        void setDefaultRenderer(Class<?> columnClass, TableCellRenderer renderer);
    }

}
