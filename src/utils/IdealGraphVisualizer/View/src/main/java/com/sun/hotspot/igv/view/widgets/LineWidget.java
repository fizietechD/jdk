/*
 * Copyright (c) 2008, 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 *
 */
package com.sun.hotspot.igv.view.widgets;

import com.sun.hotspot.igv.graph.Block;
import com.sun.hotspot.igv.graph.Connection;
import com.sun.hotspot.igv.graph.Figure;
import com.sun.hotspot.igv.graph.OutputSlot;
import com.sun.hotspot.igv.layout.Vertex;
import com.sun.hotspot.igv.util.StringUtils;
import com.sun.hotspot.igv.view.DiagramScene;
import java.awt.*;
import java.awt.geom.CubicCurve2D;
import java.awt.geom.Line2D;
import java.util.*;
import java.util.List;
import javax.swing.JPopupMenu;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;

import com.sun.hotspot.igv.view.actions.CustomSelectAction;
import org.netbeans.api.visual.action.ActionFactory;
import org.netbeans.api.visual.action.PopupMenuProvider;
import org.netbeans.api.visual.action.SelectProvider;
import org.netbeans.api.visual.model.ObjectState;
import org.netbeans.api.visual.widget.Widget;

/**
 *
 * @author Thomas Wuerthinger
 */
public class LineWidget extends Widget implements PopupMenuProvider {

    public final int BORDER = 5;
    public final int ARROW_SIZE = 6;
    public final int BOLD_ARROW_SIZE = 7;
    public final int HOVER_ARROW_SIZE = 8;
    public final int BOLD_STROKE_WIDTH = 2;
    public final int HOVER_STROKE_WIDTH = 3;
    private static final double ZOOM_FACTOR = 0.1;
    private final OutputSlot outputSlot;
    private final DiagramScene scene;
    private final List<? extends Connection> connections;
    private Point from;
    private Point to;
    private Rectangle clientArea;
    private final LineWidget predecessor;
    private final List<LineWidget> successors;
    private boolean highlighted;
    private boolean popupVisible;
    private final boolean isBold;
    private final boolean isDashed;
    private boolean needToInitToolTipText = true;
    private int fromControlYOffset;
    private int toControlYOffset;

    public LineWidget(DiagramScene scene, OutputSlot s, List<? extends Connection> connections, Point from, Point to, LineWidget predecessor, boolean isBold, boolean isDashed) {
        super(scene);
        this.scene = scene;
        this.outputSlot = s;
        this.connections = Collections.unmodifiableList(connections);
        this.from = from;
        this.to = to;
        this.predecessor = predecessor;
        this.successors = new ArrayList<>();
        if (predecessor != null) {
            predecessor.addSuccessor(this);
        }

        this.isBold = isBold;
        this.isDashed = isDashed;

        computeClientArea();

        Color color = Color.BLACK;
        if (!connections.isEmpty()) {
            color = connections.get(0).getColor();
        }

        setCheckClipping(false);

        getActions().addAction(ActionFactory.createPopupMenuAction(this));
        setBackground(color);

        getActions().addAction(new CustomSelectAction(new SelectProvider() {

            @Override
            public boolean isAimingAllowed(Widget widget, Point localLocation, boolean invertSelection) {
                return true;
            }

            @Override
            public boolean isSelectionAllowed(Widget widget, Point localLocation, boolean invertSelection) {
                return true;
            }

            @Override
            public void select(Widget widget, Point localLocation, boolean invertSelection) {
                Set<Vertex> vertexSet = new HashSet<>();
                for (Connection connection : connections) {
                    if (connection.hasSlots()) {
                        vertexSet.add(connection.getTo().getVertex());
                        vertexSet.add(connection.getFrom().getVertex());
                    }
                }
                scene.userSelectionSuggested(vertexSet, invertSelection);
            }
        }));
    }

    public Point getClientAreaLocation() {
        return clientArea.getLocation();
    }

    private void computeClientArea() {
        int minX = from.x;
        int minY = from.y;
        int maxX = to.x;
        int maxY = to.y;

        // Ensure min and max values are correct
        if (minX > maxX) {
            int tmp = minX;
            minX = maxX;
            maxX = tmp;
        }

        if (minY > maxY) {
            int tmp = minY;
            minY = maxY;
            maxY = tmp;
        }

        // Set client area to include the curve and add a BORDER for extra space
        clientArea = new Rectangle(minX, minY, maxX - minX + 1, maxY - minY + 1);
        clientArea.grow(BORDER, BORDER);
    }

    private String generateToolTipText(List<? extends Connection> conn) {
        StringBuilder sb = new StringBuilder();
        for (Connection c : conn) {
            sb.append(StringUtils.escapeHTML(c.getToolTipText()));
            sb.append("<br>");
        }
        return sb.toString();
    }

    public void setFrom(Point from) {
        this.from = from;
        computeClientArea();
    }

    public void setTo(Point to) {
        this.to= to;
        computeClientArea();
    }

    public void setFromControlYOffset(int fromControlYOffset) {
        this.fromControlYOffset = fromControlYOffset;
        computeClientArea();
    }

    public void setToControlYOffset(int toControlYOffset) {
        this.toControlYOffset = toControlYOffset;
        computeClientArea();
    }

    public Point getFrom() {
        return from;
    }

    public Point getTo() {
        return to;
    }

    public LineWidget getPredecessor() {
        return predecessor;
    }

    public List<LineWidget> getSuccessors() {
        return Collections.unmodifiableList(successors);
    }

    private void addSuccessor(LineWidget widget) {
        this.successors.add(widget);
    }

    @Override
    protected Rectangle calculateClientArea() {
        return clientArea;
    }

    @Override
    protected void paintWidget() {
        if (scene.getZoomFactor() < ZOOM_FACTOR) {
            return;
        }

        Graphics2D g = this.getGraphics();
        g.setPaint(this.getBackground());
        float width = 1.0f;

        if (isBold) {
            width = BOLD_STROKE_WIDTH;
        }

        if (highlighted || popupVisible) {
            width = HOVER_STROKE_WIDTH;
        }

        Stroke oldStroke = g.getStroke();
        if (isDashed) {
            float[] dashPattern = {5, 5, 5, 5};
            g.setStroke(new BasicStroke(width, BasicStroke.CAP_BUTT,
                    BasicStroke.JOIN_MITER, 10,
                    dashPattern, 0));
        } else {
            g.setStroke(new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_MITER));
        }

        // Define S-shaped curve with control points
        if (fromControlYOffset != 0 && toControlYOffset != 0) {
            if (from.y < to.y) { // non-reversed edges
                if (Math.abs(from.x - to.x) > 10) {
                    CubicCurve2D.Float sShape = new CubicCurve2D.Float();
                    sShape.setCurve(from.x, from.y,
                            from.x, from.y + fromControlYOffset,
                            to.x, to.y + toControlYOffset,
                            to.x, to.y);
                    g.draw(sShape);
                } else {
                    g.drawLine(from.x, from.y, to.x, to.y);
                }
            } else {  // reverse edges
                if (from.x - to.x > 0) {
                    CubicCurve2D.Float sShape = new CubicCurve2D.Float();
                    sShape.setCurve(from.x, from.y,
                            from.x - 150, from.y + fromControlYOffset,
                            to.x + 150, to.y + toControlYOffset,
                            to.x, to.y);
                    g.draw(sShape);
                } else {
                    // add x offset
                    CubicCurve2D.Float sShape = new CubicCurve2D.Float();
                    sShape.setCurve(from.x, from.y,
                            from.x + 150, from.y + fromControlYOffset,
                            to.x - 150, to.y + toControlYOffset,
                            to.x, to.y);
                    g.draw(sShape);
                }
            }
        } else {
            // Fallback to straight line if control points are not set
            g.drawLine(from.x, from.y, to.x, to.y);
        }

        boolean sameFrom = false;
        boolean sameTo = successors.isEmpty();
        for (LineWidget w : successors) {
            if (w.getFrom().equals(getTo())) {
                sameTo = true;
                break;
            }
        }

        if (predecessor == null || predecessor.getTo().equals(getFrom())) {
            sameFrom = true;
        }


        int size = ARROW_SIZE;
        if (isBold) {
            size = BOLD_ARROW_SIZE;
        }
        if (highlighted || popupVisible) {
            size = HOVER_ARROW_SIZE;
        }
        if (!sameFrom) {
            g.fillPolygon(
                    new int[]{from.x - size / 2, from.x + size / 2, from.x},
                    new int[]{from.y - size / 2, from.y - size / 2, from.y + size / 2},
                    3);
        }
        if (!sameTo) {
            g.fillPolygon(
                    new int[]{to.x - size / 2, to.x + size / 2, to.x},
                    new int[]{to.y - size / 2, to.y - size / 2, to.y + size / 2},
                    3);
        }
        g.setStroke(oldStroke);
        super.paintWidget();
    }

    private void setPopupVisible(boolean b) {
        this.popupVisible = b;
        this.revalidate(true);
    }

    @Override
    public boolean isHitAt(Point localPoint) {
        return Line2D.ptLineDistSq(from.x, from.y, to.x, to.y, localPoint.x, localPoint.y) <= BORDER * BORDER;
    }

    @Override
    protected void notifyStateChanged(ObjectState previousState, ObjectState state) {
        if (previousState.isHovered() != state.isHovered()) {
            boolean enableHighlighting = state.isHovered();
            highlightPredecessors(enableHighlighting);
            setHighlighted(enableHighlighting);
            recursiveHighlightSuccessors(enableHighlighting);
            highlightVertices(enableHighlighting);
            if (enableHighlighting && needToInitToolTipText) {
                setToolTipText("<HTML>" + generateToolTipText(this.connections) + "</HTML>");
                needToInitToolTipText = false; // Ensure it's only set once
            }
        }
    }

    private void highlightPredecessors(boolean enable) {
        LineWidget predecessorLineWidget = predecessor;
        while (predecessorLineWidget != null) {
            predecessorLineWidget.setHighlighted(enable);
            predecessorLineWidget = predecessorLineWidget.predecessor;
        }
    }

    private void recursiveHighlightSuccessors(boolean enable) {
        for (LineWidget successorLineWidget : successors) {
            successorLineWidget.setHighlighted(enable);
            successorLineWidget.recursiveHighlightSuccessors(enable);
        }
    }

    private void highlightVertices(boolean enable) {
        Set<Object> highlightedObjects = new HashSet<>(scene.getHighlightedObjects());
        Set<Object> highlightedObjectsChange = new HashSet<>();
        for (Connection c : connections) {
            if (c.hasSlots()) {
                highlightedObjectsChange.add(c.getTo());
                highlightedObjectsChange.add(c.getTo().getVertex());
                highlightedObjectsChange.add(c.getFrom());
                highlightedObjectsChange.add(c.getFrom().getVertex());
            }
        }
        if (enable) {
            highlightedObjects.addAll(highlightedObjectsChange);
        } else {
            highlightedObjects.removeAll(highlightedObjectsChange);
        }
        scene.setHighlightedObjects(highlightedObjects);
    }

    private void setHighlighted(boolean enable) {
        highlighted = enable;
        revalidate(true);
    }

    private void setRecursivePopupVisible(boolean b) {
        LineWidget cur = predecessor;
        while (cur != null) {
            cur.setPopupVisible(b);
            cur = cur.predecessor;
        }

        popupVisibleSuccessors(b);
        setPopupVisible(b);
    }

    private void popupVisibleSuccessors(boolean b) {
        for (LineWidget s : successors) {
            s.setPopupVisible(b);
            s.popupVisibleSuccessors(b);
        }
    }

    @Override
    public JPopupMenu getPopupMenu(Widget widget, Point localLocation) {
        JPopupMenu menu = new JPopupMenu();
        if (outputSlot == null) { // One-to-one block line.
            assert (connections.size() == 1);
            Connection c = connections.get(0);
            menu.add(scene.createGotoAction((Block)c.getFromCluster()));
            menu.addSeparator();
            menu.add(scene.createGotoAction((Block)c.getToCluster()));
        } else { // One-to-many figure line.
            menu.add(scene.createGotoAction(outputSlot.getFigure()));
            menu.addSeparator();
            for (Connection c : connections) {
                menu.add(scene.createGotoAction((Figure)c.getTo().getVertex()));
            }
        }
        final LineWidget w = this;
        menu.addPopupMenuListener(new PopupMenuListener() {

            @Override
            public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
                w.setRecursivePopupVisible(true);
            }

            @Override
            public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
                w.setRecursivePopupVisible(false);
            }

            @Override
            public void popupMenuCanceled(PopupMenuEvent e) {
            }
        });

        return menu;
    }

}
