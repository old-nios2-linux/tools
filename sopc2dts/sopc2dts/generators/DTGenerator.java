/*
sopc2dts - Devicetree generation for Altera systems

Copyright (C) 2012 - 2013 Walter Goossens <waltergoossens@home.nl>

This library is free software; you can redistribute it and/or
modify it under the terms of the GNU Lesser General Public
License as published by the Free Software Foundation; either
version 2.1 of the License, or (at your option) any later version.

This library is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
Lesser General Public License for more details.

You should have received a copy of the GNU Lesser General Public
License along with this library; if not, write to the Free Software
Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
*/
package sopc2dts.generators;

import java.util.Collections;
import java.util.Comparator;
import java.util.Vector;

import sopc2dts.Logger;
import sopc2dts.Logger.LogLevel;
import sopc2dts.lib.AvalonSystem;
import sopc2dts.lib.AvalonSystem.SystemDataType;
import sopc2dts.lib.BoardInfo;
import sopc2dts.lib.BoardInfo.SortType;
import sopc2dts.lib.Connection;
import sopc2dts.lib.Parameter;
import sopc2dts.lib.BoardInfo.PovType;
import sopc2dts.lib.boardinfo.BICDTAppend;
import sopc2dts.lib.components.BasicComponent;
import sopc2dts.lib.components.Interface;
import sopc2dts.lib.components.MemoryBlock;
import sopc2dts.lib.components.base.CpuComponent;
import sopc2dts.lib.devicetree.DTHelper;
import sopc2dts.lib.devicetree.DTNode;
import sopc2dts.lib.devicetree.DTPropBool;
import sopc2dts.lib.devicetree.DTPropByte;
import sopc2dts.lib.devicetree.DTPropHexNumber;
import sopc2dts.lib.devicetree.DTPropNumber;
import sopc2dts.lib.devicetree.DTPropString;

public abstract class DTGenerator extends AbstractSopcGenerator {
	Vector<BasicComponent> vHandled;
	public DTGenerator(AvalonSystem s, boolean isText) {
		super(s, isText);
	}

	protected synchronized DTNode getDTOutput(BoardInfo bi)
	{
		vHandled = new Vector<BasicComponent>();
		DTNode rootNode = new DTNode("/");
		BasicComponent povComponent = getPovComponent(bi);
		DTNode sopcNode;
		DTNode chosenNode;
		if(povComponent!=null)
		{
			int addrCells = povComponent.getInterfaces(SystemDataType.MEMORY_MAPPED, true).firstElement().getPrimaryWidth();
			int sizeCells = povComponent.getInterfaces(SystemDataType.MEMORY_MAPPED, true).firstElement().getSecondaryWidth();
			if(bi.getPovType().equals(PovType.CPU))
			{
				DTNode cpuNode = getCpuNodes(bi);
				DTNode memNode = getMemoryNode(bi, povComponent, addrCells, sizeCells);
				sopcNode = new DTNode("sopc@0");
				chosenNode = getChosenNode(bi);
				DTPropString dtps = new DTPropString("model","ALTR," + sys.getSystemName());
				rootNode.addProperty(dtps);
				dtps = new DTPropString("compatible","ALTR," + sys.getSystemName());
				rootNode.addProperty(dtps);
				Vector<Parameter> vAliases = bi.getAliases();
				if (vAliases.size() > 0) {
					DTNode aliasNode = new DTNode("aliases");
					for (Parameter p : vAliases) {
						aliasNode.addProperty(new DTPropString(p.getName(),p.getValue()));
					}
				    rootNode.addChild(aliasNode);
				}
				rootNode.addChild(cpuNode);
				rootNode.addChild(memNode);
				sopcNode.addProperty(new DTPropString("device_type", "soc"));
			} else {
				sopcNode = rootNode;
				chosenNode = null;
			}
			DTPropNumber dtpn = new DTPropNumber("#address-cells", (long)addrCells);
			rootNode.addProperty(dtpn);
			dtpn = new DTPropNumber("#size-cells",(long)sizeCells);
			rootNode.addProperty(dtpn);

			sopcNode = getSlavesFor(bi, povComponent, sopcNode);
			sopcNode.addProperty(new DTPropBool("ranges"));
			sopcNode.addProperty(new DTPropNumber("#address-cells",(long)addrCells));
			sopcNode.addProperty(new DTPropNumber("#size-cells",(long)sizeCells));
			Vector<String> vCompat = new Vector<String>();
			vCompat.add("ALTR,avalon");
			vCompat.add("simple-bus");
			sopcNode.addProperty(new DTPropString("compatible", vCompat));
			sopcNode.addProperty(new DTPropNumber("bus-frequency", povComponent.getClockRate()));
			if(bi.getPovType().equals(PovType.CPU))
			{
				rootNode.addChild(sopcNode);
				rootNode.addChild(chosenNode);
			}
		}
		doDTAppend(rootNode,bi);
		return rootNode;
	}

	private void doDTAppend(DTNode rootNode, BoardInfo bi) {
		Vector<BICDTAppend> appends = bi.getDTAppends();
		for(BICDTAppend dta : appends) {
			DTNode parent = null;
			if(dta.getParentLabel()!=null) {
				parent = DTHelper.getChildByLabel(rootNode, dta.getParentLabel());
			}
			if((parent==null) && (dta.getParentPath()!=null)) {
				parent = rootNode;
				for(String nodeStr : dta.getParentPath()) {
					DTNode n = parent;
					parent = null;
					for(DTNode child : n.getChildren()) {
						if(child.getName().equalsIgnoreCase(nodeStr))
						{
							parent = child;
						}
					}
				}
			}
			if(parent==null) {
				Logger.logln("DTAppend: Unable to find parent for " + dta.getInstanceName() + ". Adding to root", LogLevel.WARNING);
				parent = rootNode;
			}
			switch(dta.getType()) {
			case NODE: {
				parent.addChild(new DTNode(dta.getInstanceName(),dta.getLabel()));
			} break;
			case PROP_BOOL: {
				parent.addProperty(new DTPropBool(dta.getInstanceName(), dta.getLabel(), "appended from boardinfo"));
			} break;
			case PROP_NUMBER: {
				parent.addProperty(new DTPropNumber(dta.getInstanceName(), 
						Long.decode(dta.getValue()), dta.getLabel(), 
						"appended from boardinfo"));
			} break;
			case PROP_HEX: {
				parent.addProperty(new DTPropHexNumber(dta.getInstanceName(), 
						Long.decode(dta.getValue()), dta.getLabel(), 
						"appended from boardinfo"));
			} break;
			case PROP_BYTE: {
				parent.addProperty(new DTPropByte(dta.getInstanceName(), 
						Integer.decode(dta.getValue()), dta.getLabel(), 
						"appended from boardinfo"));
			} break;
			case PROP_STRING: {
				parent.addProperty(new DTPropString(dta.getInstanceName(), 
						dta.getValue(), dta.getLabel(), 
						"appended from boardinfo"));
			} break;
			default: {
				Logger.logln("Unimplemented dtappend type", LogLevel.DEBUG);
			}
			}
		}
	}

	DTNode getChosenNode(BoardInfo bi)
	{
		DTNode chosenNode = new DTNode("chosen");
		if((bi.getBootArgs()==null)||(bi.getBootArgs().length()==0))
		{
			bi.setBootArgs("debug console=ttyAL0,115200");
		} else {
			bi.setBootArgs(bi.getBootArgs().replaceAll("\"", ""));
		}
		chosenNode.addProperty(new DTPropString("bootargs", bi.getBootArgs()));
		return chosenNode;
	}
	DTNode getCpuNodes(BoardInfo bi)
	{
		int numCPUs = 0;
		DTNode cpuNode = new DTNode("cpus");
		if(bi.getPovType() == PovType.CPU)
		{
			cpuNode.addProperty(new DTPropNumber("#address-cells",1L));
			cpuNode.addProperty(new DTPropNumber("#size-cells",0L));
			for(BasicComponent comp : sys.getSystemComponents())
			{
				if(comp instanceof CpuComponent)
				{
					CpuComponent cpu = (CpuComponent)comp;
					if(bi.getPov()==null) {
						bi.setPov(comp.getInstanceName());
					}
					cpu.setCpuIndex(numCPUs);
					cpuNode.addChild(comp.toDTNode(bi, null));
					vHandled.add(comp);
					numCPUs++;
				}
			}
		}
		if(cpuNode.getChildren().isEmpty())
		{
			cpuNode = null;
		}
		return cpuNode;
	}
	DTNode getMemoryNode(BoardInfo bi, BasicComponent master, int addrCells, int sizeCells)
	{
		DTNode memNode = new DTNode("memory@0");
		DTPropHexNumber dtpReg = new DTPropHexNumber("reg");
		dtpReg.getValues().clear();
		dtpReg.setNumValuesPerRow(addrCells + sizeCells);
		memNode.addProperty(new DTPropString("device_type", "memory"));
		memNode.addProperty(dtpReg);
		if(master!=null)
		{
			Vector<String> vMemoryMapped = bi.getMemoryNodes();
			if(vMemoryMapped!=null)
			{
				for(Interface intf : master.getInterfaces())
				{
					if(intf.isMemoryMaster())
					{
						for(MemoryBlock mem : intf.getMemoryMap())
						{
							if(vMemoryMapped.contains(mem.getModule().getInstanceName()))
							{
								BasicComponent comp = mem.getModule();
								if((comp!=null)&&(!vHandled.contains(comp)))
								{
									dtpReg.addValues(mem.getBase());
									dtpReg.addValues(mem.getSize());
									vHandled.add(comp);
								}		
							}
						}
					}
				}
			}

			if(dtpReg.getValues().size()==0)
			{
				Logger.logln("dts memory section: No memory nodes specified. " +
						"Blindly adding them all", LogLevel.INFO);
				/*
				 * manual memory-map failed or is not present.
				 * Just list all devices classified as "memory"
				 */
				vMemoryMapped = new Vector<String>();
				for(Interface intf : master.getInterfaces())
				{
					if(intf.isMemoryMaster())
					{
						for(MemoryBlock mem : intf.getMemoryMap())
						{
							if(!vMemoryMapped.contains(mem.getModuleName()))
							{
								BasicComponent comp = mem.getModule();
								if(comp!=null)
								{
									if(comp.getScd().getGroup().equalsIgnoreCase("memory"))
									{
										dtpReg.addValues(mem.getBase());
										dtpReg.addValues(mem.getSize());
										vMemoryMapped.add(mem.getModuleName());
										vHandled.add(comp);
									}
								}		
							}
						}
					}
				}
			}
		}
		if(dtpReg.getValues().size()==0) {
			return null;
		} else {
			return memNode;
		}
	}
	
	DTNode getSlavesFor(BoardInfo bi, BasicComponent masterComp, DTNode masterNode)
	{
		if(masterComp!=null)
		{
			Vector<Connection> vSlaveConn = masterComp.getConnections(SystemDataType.MEMORY_MAPPED, true);
			sortSlaves(vSlaveConn, bi.getSortType());
			for(Connection conn : vSlaveConn)
			{
				BasicComponent slave = conn.getSlaveModule();						
				if((slave!=null)&&(!vHandled.contains(slave)))
				{
					vHandled.add(slave);
					if(slave.getScd().getGroup().equals("bridge"))
					{
						DTNode bridgeNode = getSlavesFor(bi, slave, slave.toDTNode(bi, conn));
						//Don't add empty bridges...
						if(!bridgeNode.getChildren().isEmpty())
						{
							masterNode.addChild(bridgeNode);
						}
					} else {
						masterNode.addChild(slave.toDTNode(bi, conn));
					}
				}
			}
		}
		return masterNode;
	}
	protected static void sortSlaves(Vector<Connection> vConn, final SortType sort) {
		if(!sort.equals(SortType.NONE)) {
			Collections.sort(vConn, new Comparator<Connection>() {

				public int compare(Connection c1, Connection c2) {
					switch(sort) {
					case ADDRESS:
						return DTHelper.longArrCompare(c1.getConnValue(), c2.getConnValue());
					case NAME:
						int cmp = c1.getSlaveModule().getScd().getGroup().compareToIgnoreCase(c2.getSlaveModule().getScd().getGroup());
						if(cmp!=0) {
							return cmp;
						}
						/* Fallthrough and decide by label */
					case LABEL:
						return c1.getSlaveModule().getInstanceName().compareToIgnoreCase(c2.getSlaveModule().getInstanceName());
					default: return 0;
					}
				}
			});
		}
	}
}
