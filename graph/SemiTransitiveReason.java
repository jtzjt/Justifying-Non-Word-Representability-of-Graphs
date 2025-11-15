package graph;

import path.Path;
import java.util.List;
import java.util.ArrayList;

/***BEGIN CLASS SemiTransitiveReason.java***************************************
 * Simple class that encapsulates the result of checking if an orientation is
 * semi-transitive, and a reason why, if it is not.
 * 
 * @author julia
 ******************/public class SemiTransitiveReason {/************************/

public static enum Reason {	SEMI_TRANSITIVE, CYCLE, SHORTCUT;};
public final Reason reason;
public final Path path;	//either a cycle or shortcut. (kept for backward compatibility)
public final List<Path> cycles;	//all cycles found
public final List<Path> shortcuts;	//all shortcuts found


SemiTransitiveReason (Reason r, Path p)
{	reason = r;	path = p;
	cycles = new ArrayList<Path>();
	shortcuts = new ArrayList<Path>();
	if (p != null) {
		if (r == Reason.CYCLE)
			cycles.add(p);
		else if (r == Reason.SHORTCUT)
			shortcuts.add(p);
	}
}


SemiTransitiveReason (Reason r, List<Path> cyclesList, List<Path> shortcutsList)
{	reason = r;
	cycles = cyclesList != null ? cyclesList : new ArrayList<Path>();
	shortcuts = shortcutsList != null ? shortcutsList : new ArrayList<Path>();
	path = (!cycles.isEmpty()) ? cycles.get(0) : 
		   (!shortcuts.isEmpty()) ? shortcuts.get(0) : null;
}


/* return whether semi-transitive or not
 */
public boolean result()
{	return reason.equals(Reason.SEMI_TRANSITIVE);
}

/******************/}/********************END CLASS SemiTransitiveReason.java***/
