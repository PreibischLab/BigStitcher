package net.preibisch.mvrecon.process.export;

import net.preibisch.mvrecon.fiji.plugin.fusion.FusionExportInterface;

public class DisplayImageGui extends DisplayImage implements ImgExportGui {

	@Override
	public boolean queryParameters( final FusionExportInterface fusion ) { return true; }

	@Override
	public ImgExportGui newInstance() {
		return new DisplayImageGui();
	}
}
