package org.e2immu.analyzer.shallow.aapi.javax;

import org.e2immu.annotation.Commutable;
import org.e2immu.annotation.Modified;

import javax.swing.table.TableCellRenderer;
import java.awt.event.ActionListener;

public class JavaxSwingTable {
    public static final String PACKAGE_NAME = "javax.swing.table";

    interface TableColumn$ {
        @Commutable
        @Modified
        void setPreferredWidth(int preferredWidth);
    }
}
