package eu.hassanlab.rdnwdp

import org.scijava.command.Command
import org.scijava.plugin.Plugin

@Plugin(type = Command.class, menuPath = "File>RotLikeHell")
class RotLikeHell implements Command {
    @Override
    void run() {
        print "It's already rotten"
    }
}
