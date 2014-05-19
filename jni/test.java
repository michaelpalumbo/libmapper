
import Mapper.*;
import Mapper.Device.*;
import java.util.Arrays;

class test {
    public static void main(String [] args) {
        final Device dev = new Device("javatest");
        final Monitor mon = new Monitor(Mapper.Monitor.SUB_DEVICE_ALL);

        // This is how to ensure the device is freed when the program
        // exits, even on SIGINT.  The Device must be declared "final".
        Runtime.getRuntime().addShutdownHook(new Thread()
            {
                @Override
                public void run()
                    {
                        dev.free();
                        mon.free();
                    }
            });

        mon.Db.addDeviceCallback(new Mapper.Db.DeviceListener() {
            public void onEvent(Mapper.Db.Device d, int event) {
                System.out.println("db onEvent() for device "+d.name());
            }});

        mon.Db.addSignalCallback(new Mapper.Db.SignalListener() {
            public void onEvent(Mapper.Db.Signal s, int event) {
                System.out.println("db onEvent() for signal "+s.name());
            }});

        mon.Db.addLinkCallback(new Mapper.Db.LinkListener() {
            public void onEvent(Mapper.Db.Link l, int event) {
                System.out.println("db onEvent() for link "
                                   +l.srcName()+" -> "+l.destName());
            }});

        mon.Db.addConnectionCallback(new Mapper.Db.ConnectionListener() {
            public void onEvent(Mapper.Db.Connection c, int event) {
                System.out.println("db onEvent() for connection "
                                   +c.srcName+" -> "+c.destName+" @expr "
                                   +c.expression);
            }});

        Mapper.Device.Signal inp1 = dev.add_input("insig1", 1, 'f', "Hz",
                                                  new PropertyValue('f', 2.0),
                                                  null, new InputListener() {
                public void onInput(Mapper.Device.Signal sig,
                                    Mapper.Db.Signal props,
                                    int instance_id,
                                    float[] v,
                                    TimeTag tt) {
                    System.out.println(" >> in onInput() for "+props.name()+": "
                                       +Arrays.toString(v));
                }});

        System.out.println("Input signal name: "+inp1.name());

        Signal out1 = dev.add_output("outsig1", 1, 'i', "Hz",
                                     new PropertyValue('i', 0.0),
                                     new PropertyValue('i', 1.0));
        Signal out2 = dev.add_output("outsig2", 1, 'f', "Hz",
                                     new PropertyValue(0.0f),
                                     new PropertyValue(1.0f));

        System.out.println("Output signal index: "+out1.index());
        System.out.println("Zeroeth output signal name: "+dev.get_output_by_index(0).name());

        dev.set_property("width", new PropertyValue(256));
        dev.set_property("height", new PropertyValue(12.5));
        dev.set_property("depth", new PropertyValue("67"));
        dev.set_property("deletethis", new PropertyValue("should not see me"));
        dev.remove_property("deletethis");

        out1.set_property("width", new PropertyValue(new int[] {10, 11, 12}));
        out1.set_property("height", new PropertyValue(6.25));
        out1.set_property("depth", new PropertyValue(new String[]{"one","two"}));
        out1.set_property("deletethis", new PropertyValue("or me"));
        out1.remove_property("deletethis");
        out1.set_minimum(new PropertyValue(12));

        System.out.println("Signal properties:");
        System.out.println("  Name of out1: " + out1.properties().name());

        System.out.println("  Looking up `height': "
                           + out1.properties().property("height"));
        System.out.println("  Looking up `width': "
                           + out1.properties().property("width"));
        System.out.println("  Looking up `depth': "
                           + out1.properties().property("depth"));
        System.out.println("  Looking up `deletethis': "
                           + out1.properties().property("deletethis")
                           + " (should be null)");
        System.out.println("  Looking up minimum: "
                           + out1.properties().minimum());
        System.out.println("  Looking up maximum: "
                           + out1.properties().maximum());

        System.out.println("Waiting for ready...");
        while (!dev.ready()) {
            dev.poll(100);
        }
        System.out.println("Device is ready.");

        System.out.println("Device name: "+dev.name());
        System.out.println("Device port: "+dev.port());
        System.out.println("Device ordinal: "+dev.ordinal());
        System.out.println("Device interface: "+dev.iface());
        System.out.println("Device ip4: "+dev.ip4());

        mon.link(dev.name(), dev.name(), null);
        while (dev.num_links_in() <= 0) { dev.poll(100); }

        Mapper.Db.Connection c = new Mapper.Db.Connection();
        c.mode = Mapper.Db.Connection.MO_EXPRESSION;
        c.expression = "y=x*100";
        c.srcMin = new PropertyValue(15);
        c.srcMax = new PropertyValue(-15);
        c.destMax = new PropertyValue(1000);
        c.destMin = new PropertyValue(-2000);
        c.boundMin = Mapper.Db.Connection.BA_FOLD;
        mon.connect(dev.name()+out1.name(), dev.name()+inp1.name(), c);
        while ((dev.num_connections_in()) <= 0) { dev.poll(100); }

        int i = 0;
        double [] ar = new double [] {0};
        TimeTag tt = new TimeTag(0,0);

        // Signal should report no value before the first update.
        if (out1.value(ar, tt))
            System.out.println("Signal has value: " + ar[0]);
        else
            System.out.println("Signal has no value.");

        // Just to test vector-valued signal and timetag support,
        out1.update(new int []{i}, TimeTag.NOW);

        // Test instances
        out1.set_instance_event_callback(new InstanceEventListener() {
                public void onEvent(Mapper.Device.Signal sig,
                                    Mapper.Db.Signal props,
                                    int instance_id,
                                    int event)
                    {
                        System.out.println("Instance "
                                           + instance_id
                                           + " event " + event);
                    }
            }, InstanceEventListener.IN_ALL);

        System.out.println(inp1.name() + " allocation mode: "
                           + inp1.instance_allocation_mode());
        inp1.set_instance_allocation_mode(Device.Signal.IN_STEAL_NEWEST);
        System.out.println(inp1.name() + " allocation mode: "
                           + inp1.instance_allocation_mode());

        out1.reserve_instances(new int[]{10, 11, 12});
        out1.update_instance(10, new int[]{-8});
        out1.instance_value(10, new int[]{0});
        out1.release_instance(10);

        out2.reserve_instances(3);
        out2.update_instance(1, new float[]{21.9f});
        out2.instance_value(1, new float[]{0});
        out2.update_instance(1, new double[]{48.12});
        out2.instance_value(1, new double[]{0});
        out2.release_instance(1);

        inp1.reserve_instances(3, new InputListener() {
                public void onInput(Mapper.Device.Signal sig,
                                    Mapper.Db.Signal props,
                                    int instance_id,
                                    float[] v,
                                    TimeTag tt) {
                    System.out.println("in onInput() for "
                                       +props.name()+" instance "
                                       +instance_id+": "
                                       +Arrays.toString(v));
                }});
        System.out.println(inp1.name() + " instance 1 cb is "
                           + inp1.get_instance_callback(1));
        inp1.set_instance_callback(1, new InputListener() {
                public void onInput(Mapper.Device.Signal sig,
                                    Mapper.Db.Signal props,
                                    int instance_id,
                                    float[] v,
                                    TimeTag tt) {
                    System.out.println("in onInput() for "
                                       +props.name()+" instance 1: "
                                       +Arrays.toString(v));
                }});
        System.out.println(inp1.name() + " instance 1 cb is "
                           + inp1.get_instance_callback(1));
        inp1.set_instance_callback(1, null);
        System.out.println(inp1.name() + " instance 1 cb is "
                           + inp1.get_instance_callback(1));

        while (i <= 100) {
            System.out.print("Updated value to: " + i);
            out1.update(i);

            // Note, we are testing an implicit cast from int to float
            // here because we are passing a double[] into
            // out1.value().
            if (out1.value(ar, tt))
                System.out.print("  Signal has value: " + ar[0]);
            else
                System.out.print("  Signal has no value.");

            dev.poll(50);
            mon.poll(50);
            i++;
        }

        System.out.println();
        System.out.println("Number of connections from "
                           + out1.name() + ": " + out1.num_connections());

        System.out.println(inp1.name() + " oldest instance is "
                           + inp1.oldest_active_instance());
        System.out.println(inp1.name() + " newest instance is "
                           + inp1.newest_active_instance());

        dev.free();
    }
}
