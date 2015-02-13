package com.ibm.ServerWizard2;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.Vector;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.widgets.TreeItem;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class TargetWizardController implements PropertyChangeListener {
	SystemModel model;
	MainDialog view;

	public TargetWizardController() {
	}

	public void init() {
		LibraryManager xmlLib = new LibraryManager();
		xmlLib.init();
		if (xmlLib.doUpdateCheck()) {
			xmlLib.update();
		}
		try {
			xmlLib.loadModel(model);
			this.initModel();
			
		} catch (Exception e) {
			String btns[] = { "Close" };
			ServerWizard2.LOGGER.severe(e.getMessage());
			MessageDialog errDlg = new MessageDialog(view.getShell(), "Error",
					null, e.getMessage(), MessageDialog.ERROR, btns, 0);
			errDlg.open();
			e.printStackTrace();
			System.exit(4);
		}
		
	}
	public void initModel() throws Exception {
		model.deleteAllInstances();
		model.addUnitInstances();
		
		String parentTargetName = "sys-sys-power8";
		Target parentTarget = model.getTargetModels().get(parentTargetName);
		if (parentTarget == null) {
			throw new Exception("Parent model " + parentTargetName
					+ " is not valid");
		}
		// Create root instance
		Target sys = new Target(parentTarget);
		sys.setPosition(0);
		model.addTarget(null, sys);
	}
	
	public void importSDR(String filename) {
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		
		Vector<SdrRecord> sdrs = new Vector<SdrRecord>();
		HashMap<Integer,HashMap<Integer,Vector<SdrRecord>>> sdrLookup = new HashMap<Integer,HashMap<Integer,Vector<SdrRecord>>>();
		
		try {
			DocumentBuilder builder = factory.newDocumentBuilder();
			builder.setErrorHandler(new XmlHandler());

			Document document = builder.parse(filename);

			NodeList deviceList = document
					.getElementsByTagName("device");

			for (int i = 0; i < deviceList.getLength(); ++i) {
				Element deviceElement = (Element) deviceList.item(i);
				SdrRecord s = new SdrRecord();
				s.readXML(deviceElement);

				HashMap<Integer,Vector<SdrRecord>> idLookup = sdrLookup.get(s.getEntityId());
				if (idLookup==null) {
					idLookup = new HashMap<Integer,Vector<SdrRecord>>();
					sdrLookup.put(s.getEntityId(), idLookup);
				}
				Vector<SdrRecord> sdrRecords = idLookup.get(s.getEntityInstance());
				if (sdrRecords==null) {
					sdrRecords = new Vector<SdrRecord>();
					idLookup.put(s.getEntityInstance(), sdrRecords);
				}
				sdrRecords.add(s);
				sdrs.add(s);
				ServerWizard2.LOGGER.info(s.toString());
			}
		} catch (Exception e) {
			MessageDialog.openError(null, "SDR Import Error", e.getMessage());
			e.printStackTrace();
		}
		try {
			HashMap<String,Boolean> instCheck = new HashMap<String,Boolean>();
			model.logData="";
			model.importSdr(null,sdrLookup,instCheck,"");
			LogViewerDialog dlg = new LogViewerDialog(null);
			dlg.setData(model.logData);
			dlg.open();
		} catch (Exception e) {
			ServerWizard2.LOGGER.severe(e.getMessage());
			MessageDialog.openError(null, "SDR Import Error", e.getMessage());
			e.printStackTrace();
		}
		/*
		HashMap<Target,Vector<String>> ipmiAttr = new HashMap<Target,Vector<String>>();
		for (SdrRecord sdr : sdrs){
			Target t = sdr.getTarget();
			Vector<String> ipmiSensors = ipmiAttr.get(t);
			if (ipmiSensors==null) {
				ipmiSensors = new Vector<String>();
				ipmiAttr.put(t, ipmiSensors);
			}
			ipmiSensors.add(String.format("0x%02x", sdr.getEntityId())+","+
					String.format("0x%02x", sdr.getSensorId()));

			//System.out.println(t.getName()+","+ipmiSensors);
		}
		for (Map.Entry<Target, Vector<String>> entry : ipmiAttr.entrySet()) {
			Target t=entry.getKey();
			String ipmiStr = "";
			Vector<String> attrs = entry.getValue();
			for (String a : attrs) {
				ipmiStr = ipmiStr+a+",";
			}
			for (int i=attrs.size();i<16;i++) {
				ipmiStr = ipmiStr+"0xFF,0xFF,";
			}
			//t.setAttributeValue("IPMI_SENSORS", ipmiStr);
			
		}*/
	}	
	
	public Target getTargetModel(String type) {
		return model.getTargetModel(type);
	}
	public void setView(MainDialog view) {
		this.view = view;
	}

	public void setModel(SystemModel model) {
		this.model = model;
	}

	public Vector<String> getEnums(String e) {
		if (model.enumerations.get(e)==null) {
			ServerWizard2.LOGGER.severe("Enum not found: "+e);
			return null;
		}
		return model.enumerations.get(e).enumList;
	}
	public Boolean isEnum(String e) {
		if (model.enumerations.get(e)==null) {
			return false;
		}
		return true;
	}

	
	public void deleteTarget(Target target) {
		//model.deleteTarget(target, model.rootTarget);
		model.deleteTarget(target);
	}

	public void addTargetInstance(Target targetModel, Target parentTarget,
			TreeItem parentItem,String nameOverride) {
		
		Target targetInstance;
		Target instanceCheck = model.getTargetInstance(targetModel.getType()); 
		if (instanceCheck!=null) {
			//target instance found of this model type
			targetInstance = new Target(instanceCheck);
			targetInstance.copyChildren(instanceCheck);
		} else {
			targetInstance = new Target(targetModel);
		}
		targetInstance.setName(nameOverride);
		model.updateTargetPosition(targetInstance, parentTarget, -1);
		try {
			model.addTarget(parentTarget, targetInstance);
			view.updateInstanceTree(targetInstance, parentItem);
		} catch (Exception e) {
			MessageDialog.openError(null, "Add Target Error", e.getMessage());
		}
	}
	public Target copyTargetInstance(Target target, Target parentTarget,Boolean incrementPosition) {
		Target newTarget = new Target(target);
		if (incrementPosition) { 
			newTarget.setPosition(newTarget.getPosition()+1);
			newTarget.setSpecialAttributes();
		}
		try {
			model.addTarget(parentTarget, newTarget);
			newTarget.copyChildren(target);
		} catch (Exception e) {
			MessageDialog.openError(null, "Add Target Error", e.getMessage());
			newTarget=null;
		}
		return newTarget;
	}
	public void deleteConnection(Target target,Target busTarget,Connection conn) {
		target.deleteConnection(busTarget,conn);
	}
	public Vector<Target> getRootTargets() {
		return model.rootTargets;
	}

	public void writeXML(String filename) {
		try {
			String tmpFilename=filename+".tmp";
			model.writeXML(tmpFilename);
			File from = new File(tmpFilename);
			File to = new File(filename);
			Files.copy( from.toPath(), to.toPath(),StandardCopyOption.REPLACE_EXISTING );
			Files.delete(from.toPath());
			ServerWizard2.LOGGER.info(filename + " Saved");
		} catch (Exception exc) {
			MessageDialog.openError(null, "Error", exc.getMessage());
			exc.printStackTrace();
		}
	}

	public void readXML(String filename) {
		try {
			model.readXML(filename);
		} catch (Exception e) {
			MessageDialog.openError(null, "Error", e.getMessage());
			e.printStackTrace();
		}
	}
	public Vector<Target> getConnectionCapableTargets() {
		return model.getConnectionCapableTargets();
	}
	public void setGlobalSetting(String path,String attribute,String value) {
		model.setGlobalSetting(path, attribute, value);
	}
	public Field getGlobalSetting(String path,String attribute) {
		return model.getGlobalSetting(path, attribute);
	}
	public HashMap<String,Field> getGlobalSettings(String path) {
		return model.getGlobalSettings(path);
	}	
	public Vector<Target> getChildTargets(Target target) {
		//if (target.instanceModel) {
		//	return model.getChildTargetTypes("");
		//}
		return model.getChildTargetTypes(target.getType());
	}
	
	public void hideBusses(Target target) {
		target.hideBusses(model.getTargetLookup());
	}
	public Vector<Target> getVisibleChildren(Target target) {
		Vector<Target> children = new Vector<Target>();
		for (String c : target.getChildren()) {
			Target t = model.getTarget(c);
			if (t==null) {
				String msg="Invalid Child target id: "+c;
				ServerWizard2.LOGGER.severe(msg);
			}
			children.add(t);
		}
		return children;
	}

	public void initBusses(Target target) {
		model.initBusses(target);
	}
	public Vector<Target> getBusTypes() {
		return model.getBusTypes();
	}
	public void runChecks(String filename) {
		String includePath = LibraryManager.getWorkingDir()+"scripts";
		String script = LibraryManager.getWorkingDir()+"scripts"+System.getProperty("file.separator")+"processMrw.pl";
		
		String commandLine[] = {
				"perl",
				"-I",
				includePath,
				script,
				"-x",
				filename,
				"-f"
		};
		String commandLineStr="";
		for (int i=0;i<commandLine.length;i++) {
			commandLineStr=commandLineStr+commandLine[i]+" ";
		}
		ServerWizard2.LOGGER.info("Running: "+commandLineStr);
		String line;
		String msg="";
		try {
			Process proc = Runtime.getRuntime().exec(commandLine);
			proc.waitFor();
			InputStream error = proc.getErrorStream();
			InputStream stdout = proc.getInputStream();
			BufferedReader reader = new BufferedReader (new InputStreamReader(error));
			BufferedReader reader2 = new BufferedReader (new InputStreamReader(stdout));
			while ((line = reader.readLine ()) != null) {
				msg=msg+"ERROR: " + line;
				ServerWizard2.LOGGER.severe("ERROR: " + line);
			}
			while ((line = reader2.readLine ()) != null) {
				msg=msg+line+"\n";
				ServerWizard2.LOGGER.info(line);
			}
			LogViewerDialog dlg = new LogViewerDialog(null);
			dlg.setData(msg);
			dlg.open();
		} catch (Exception e){
			e.printStackTrace();
		}
	}

	public void propertyChange(PropertyChangeEvent arg0) {
		//view.setDirtyState(true);		
	}
}