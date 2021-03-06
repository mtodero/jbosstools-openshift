/******************************************************************************* 
 * Copyright (c) 2018 Red Hat, Inc. 
 * Distributed under license by Red Hat, Inc. All rights reserved. 
 * This program is made available under the terms of the 
 * Eclipse Public License v1.0 which accompanies this distribution, 
 * and is available at http://www.eclipse.org/legal/epl-v10.html 
 * 
 * Contributors: 
 * Red Hat, Inc. - initial API and implementation 
 ******************************************************************************/
package org.jboss.tools.cdk.ui.bot.test.server.wizard.download;

import static org.jboss.tools.cdk.ui.bot.test.utils.CDKTestUtils.assertSameMessage;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;

import org.eclipse.reddeer.common.condition.AbstractWaitCondition;
import org.eclipse.reddeer.common.exception.WaitTimeoutExpiredException;
import org.eclipse.reddeer.common.logging.Logger;
import org.eclipse.reddeer.common.wait.TimePeriod;
import org.eclipse.reddeer.common.wait.WaitUntil;
import org.eclipse.reddeer.eclipse.selectionwizard.NewMenuWizard;
import org.eclipse.reddeer.eclipse.wst.server.ui.wizard.NewServerWizardPage;
import org.eclipse.reddeer.jface.wizard.WizardDialog;
import org.eclipse.reddeer.swt.api.Link;
import org.eclipse.reddeer.swt.condition.ControlIsEnabled;
import org.eclipse.reddeer.swt.condition.ShellIsAvailable;
import org.jboss.tools.cdk.reddeer.core.condition.SystemJobIsRunning;
import org.jboss.tools.cdk.reddeer.core.enums.CDKVersion;
import org.jboss.tools.cdk.reddeer.core.label.CDKLabel;
import org.jboss.tools.cdk.reddeer.core.matcher.JobMatcher;
import org.jboss.tools.cdk.reddeer.server.ui.wizard.NewCDK32ServerWizardPage;
import org.jboss.tools.cdk.reddeer.server.ui.wizard.NewCDK3ServerWizardPage;
import org.jboss.tools.cdk.reddeer.server.ui.wizard.NewCDKServerWizard;
import org.jboss.tools.cdk.reddeer.server.ui.wizard.NewMinishiftServerWizardPage;
import org.jboss.tools.cdk.ui.bot.test.server.wizard.CDKServerWizardAbstractTest;
import org.jboss.tools.cdk.ui.bot.test.utils.CDKTestUtils;
import org.jboss.tools.cdk.ui.bot.test.utils.DownloadCDKRuntimesUtility;
import org.junit.After;
import org.junit.Before;

/**
 * Abstract class providing core test features for CDK runtime download tests
 * @author odockal
 *
 */
public class DownloadContainerRuntimeAbstractTest extends CDKServerWizardAbstractTest {

	private WizardDialog dialog;
	
	private static final Logger log = Logger.getLogger(CDK32DownloadRuntimeTest.class);
	
	@Override
	protected String getServerAdapter() {
		return null;
	}
	
	@Before
	public void beforeDownloadRuntime() {
		dialog = (NewCDKServerWizard)CDKTestUtils.openNewServerWizardDialog();
	}
	
	@After
	public void afterDownloadRuntime() {
    CDKTestUtils.deleteFilesIfExist(new File(RUNTIMES_DIRECTORY));
	}
	
	public NewCDK3ServerWizardPage chooseCDKWizardPage(CDKVersion version) {
		switch (version.serverName()) {
			case CDKLabel.Server.CDK3_SERVER_NAME:
				return new NewCDK3ServerWizardPage();
			case CDKLabel.Server.CDK32_SERVER_NAME:
				return new NewCDK32ServerWizardPage();
			case CDKLabel.Server.MINISHIFT_SERVER_NAME:
				return new NewMinishiftServerWizardPage();
			default:
				return null;
		}
	}
	
	public void downloadAndVerifyContainerRuntime(CDKVersion version, String username, String password, 
			String installFolder, String downloadFolder, boolean removeArtifacts, boolean useDefaults) {
		chooseWizardPage(CDKLabel.Server.SERVER_TYPE_GROUP, version.serverName());
		NewCDK3ServerWizardPage wizardPage = inicializeDownloadRutimeDialog(version);
		DownloadCDKRuntimesUtility util;
		if (useDefaults) {
			util = new DownloadCDKRuntimesUtility(true);
		} else {
			util = new DownloadCDKRuntimesUtility(installFolder, downloadFolder, removeArtifacts);
		}
		util.chooseRuntimeToDownload(version);
		if (!(wizardPage instanceof NewMinishiftServerWizardPage)) {
			util.processCredentials(username, password);
		}
		util.acceptLicense();
		util.downloadRuntime();
		try {
			new WaitUntil(new AbstractWaitCondition() {
				@Override
				public boolean test() {
					return wizardPage.getMinishiftBinaryLabeledText().getText().contains(version.downloadName());
				}
			}, TimePeriod.DEFAULT);
			new WaitUntil(new SystemJobIsRunning(new JobMatcher(CDKLabel.Job.MINISHIFT_VALIDATION_JOB)), TimePeriod.DEFAULT, false);
		} catch (WaitTimeoutExpiredException waitExc) {
			fail("Expected this path "  + wizardPage.getMinishiftBinaryLabeledText().getText() + " to contain value: " + version.downloadName());
		}
		// verify that generated binary is valid
		assertSameMessage((NewMenuWizard)dialog, CDKLabel.Messages.SERVER_ADAPTER_REPRESENTING);
		// add test for downloaded artifacts - temporary
		if (removeArtifacts || useDefaults) {
			try {
				assertFalse("Downloaded artifacts were not deleted from " + util.getDownloadFolder(), 
						verifyFileInFolder(util.getDownloadFolder(), version.downloadName()));
			} catch (AssertionError err) {
				log.error("Skipped due to JBIDE-25867");
			}
		} else {
			assertTrue("Downloaded artifacts were deleted from " + util.getDownloadFolder(), 
					verifyFileInFolder(util.getDownloadFolder(), version.downloadName()));
		}
		// add check that file was really downloaded, extracted and is readable
		assertTrue("Installation folder does not contain downloaded file " + version.downloadName(), 
				verifyFileInFolder(util.getInstallFolder(), version.downloadName()));
		dialog.finish();
	}
	
	public void downloadAndVerifyCDKRuntime(CDKVersion version, String username, String password) {
		downloadAndVerifyContainerRuntime(version, username, password, 
				RUNTIMES_DIRECTORY + separator + version.name(),
				RUNTIMES_DIRECTORY + separator + "tmp", 
				true, false);
	}

	public void chooseWizardPage(String... pathToServer) {
		log.info("Opening server wizard for " + pathToServer);
		NewServerWizardPage page = new NewServerWizardPage(dialog);
		
		page.selectType(pathToServer);
		page.setName(getServerAdapter());
		dialog.next();
	}
	
	public NewCDK3ServerWizardPage inicializeDownloadRutimeDialog(CDKVersion version) {
		log.info("Inicializing " + version.name() + " wizard page");
		NewCDK3ServerWizardPage wizardPage = chooseCDKWizardPage(version);
		Link link = wizardPage.getDownloadAndInstallLink();
		new WaitUntil(new ControlIsEnabled(link), TimePeriod.MEDIUM, false);
		link.click();
		new WaitUntil(new ShellIsAvailable(CDKLabel.Shell.DOWNLOAD_RUNTIMES), TimePeriod.MEDIUM);
		return wizardPage;
	}
	
	public boolean verifyFileInFolder(String folderPath, String fileName) {
		log.info("Verify that " + folderPath + " contains file containing name " + fileName);
		File folder = new File(folderPath);
		if (!folder.exists())
			return false;
		for (File file : folder.listFiles()) {
			if (file.getAbsolutePath().contains(fileName)) {
				return true;
			}
		}
		return false;
	}
	
}
