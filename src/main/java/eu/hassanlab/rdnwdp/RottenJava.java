package eu.hassanlab.rdnwdp;

import org.scijava.command.Command;
import org.scijava.plugin.Plugin;

@Plugin(type = Command.class, menuPath = "Plugins>RottenJava")
class RottenJava implements Command {
    @Override
    public void run() {
        System.out.print("It's already rotten");
    }
}
