package net.preibisch.mvrecon.fiji.spimdata.explorer.bdv;

import java.awt.event.ActionEvent;

import bdv.tools.ToggleDialogAction;

public class ToggleDialogActionBrightness extends ToggleDialogAction
{
	final ScrollableBrightnessDialog dialog;

	public ToggleDialogActionBrightness( final String name, final ScrollableBrightnessDialog dialog )
	{
		super( name, dialog );

		this.dialog = dialog;
	}

	@Override
	public void actionPerformed( final ActionEvent arg0 )
	{
		updatePanels();
		super.actionPerformed( arg0 );
	}

	public void updatePanels()
	{
		if ( this.dialog.isVisible() )
			this.dialog.updatePanels();
	}

	private static final long serialVersionUID = 1L;
}
