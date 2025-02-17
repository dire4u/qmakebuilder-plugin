package hudson.plugins.qmake;

import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import javax.servlet.ServletException;
import java.io.File;
import java.io.PrintStream;
import java.io.IOException;

/**
 * Executes <tt>qmake</tt> and <tt>make</tt> as the build process for a QT-based
 * build.
 *
 * @author Tyler Mace
 */
public class QmakeBuilder extends Builder {

	private String projectFile;
	private String buildDir;
	private String extraArguments;
	@Deprecated
	private transient String extraConfig; // unused, deprecated
	private boolean cleanBuild;

	private QmakeBuilderImpl builderImpl;

	@DataBoundConstructor
	public QmakeBuilder(String projectFile, String buildDir, String extraArguments) {
		this.projectFile = projectFile;
		this.buildDir = buildDir;
		this.extraArguments = extraArguments;
		this.cleanBuild = false;
		builderImpl = new QmakeBuilderImpl();
	}

	public String getProjectFile() {
		return this.projectFile;
	}

	public String getExtraArguments() {
		return this.extraArguments;
	}

	public boolean getCleanBuild() {
		return this.cleanBuild;
	}

	@Override
	public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener)
			throws InterruptedException, IOException {
		PrintStream logger = listener.getLogger();

		logger.println("MODULE: " + build.getModuleRoot());

		final boolean isWindows = !launcher.isUnix();

		if (builderImpl == null) {
			builderImpl = new QmakeBuilderImpl();
		}
		EnvVars envVars = build.getEnvironment(listener);

		String theProjectFile;
		try {
			theProjectFile = builderImpl.preparePath(envVars, this.projectFile, isWindows, logger);
		} catch (IOException ioe) {
			logger.println("IO Exception with the project file: " + projectFile);
			logger.println(ioe.getMessage());
			return false;
		}

		builderImpl.setQmakeBin(envVars, getDescriptor().qmakePath(), isWindows);

		String qmakeCall = builderImpl.buildQMakeCall(theProjectFile, extraArguments);

		FilePath workDir = new FilePath(build.getWorkspace(), buildDir);
		//workDir = workDir.getParent();
		logger.println("QMake call : " + qmakeCall);

		try {
			int result = launcher.launch().cmds(Util.tokenize(qmakeCall)).envs(envVars).stdout(logger).pwd(workDir)
					.join();
			if (result != 0)
				return false;

			String makeExe = "make";
			if (!launcher.isUnix())
				makeExe = "nmake";

			result = launcher.launch().cmds(makeExe).envs(envVars).stdout(logger).pwd(workDir).join();

			if (result != 0) {
				return false;
			} else {
				return true;
			}
		} catch (IOException e) {
			e.printStackTrace();
		} catch (InterruptedException ie) {
			ie.printStackTrace();
		}
		return false;
	}

	@Override
	public DescriptorImpl getDescriptor() {
		return (DescriptorImpl) super.getDescriptor();
	}

	/**
	 * @return the buildDir
	 */
	public String getBuildDir() {
		return buildDir;
	}

	/**
	 * Descriptor for {@link QmakeBuilder}. Used as a singleton. The class is marked
	 * as public so that it can be accessed from views.
	 *
	 * <p>
	 * See <tt>views/hudson/plugins/hello_world/QmakeBuilder/*.jelly</tt> for the
	 * actual HTML fragment for the configuration screen.
	 */
	@Extension
	public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {
		/**
		 * To persist global configuration information, simply store it in a field and
		 * call save().
		 *
		 * <p>
		 * If you don't want fields to be persisted, use <tt>transient</tt>.
		 */
		private String qmakePath;

		public DescriptorImpl() {
			super(QmakeBuilder.class);
			load();
		}

		/**
		 * Performs on-the-fly validation of the form field 'projectFile'.
		 *
		 * @param value
		 */
		public FormValidation doCheckProjectFile(@QueryParameter final String value)
				throws IOException, ServletException {
			if (value.length() == 0)
				return FormValidation.error("Please set a project file");
			if (value.length() < 1)
				return FormValidation.warning("Isn't the name too short?");

			File file = new File(value);
			if (file.isDirectory())
				return FormValidation.error("Project file is a directory");

			// TODO add more checks
			return FormValidation.ok();
		}

		/**
		 * This human readable name is used in the configuration screen.
		 */
		public String getDisplayName() {
			return "QMake Build";
		}

		@Override
		public boolean isApplicable(Class<? extends AbstractProject> jobType) {
			return true;
		}

		@Override
		public boolean configure(StaplerRequest req, JSONObject o) throws FormException {
			// to persist global configuration information,
			// set that to properties and call save().
			qmakePath = o.getString("qmakePath");
			save();
			return super.configure(req, o);
		}

		public String qmakePath() {
			return qmakePath;
		}
	}
}
