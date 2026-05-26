package Base.MultisellAnnunces;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.logging.Logger;

import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

public class AnnouceMultiSell
{
    private static AnnouceMultiSell _instance = null;

    private static HashMap<Integer, HashMap<String,Object>> getList = new HashMap<>();

    private static Logger _log = Logger.getLogger(AnnouceMultiSell.class.getName());

    public static AnnouceMultiSell getInstance ()
    {
        if (_instance == null)
        {
            _instance = new AnnouceMultiSell();
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setValidating(false);
            factory.setIgnoringComments(true);

            File file = new File("data/xml/announceMultiSell.xml");

            // ✔️ LOG: file existence check
            if (!file.exists())
            {
                _log.warning("announceMultiSell: Error! announceMultiSell.xml file does not exist in data/xml!");
                return _instance;
            }

            _log.info("announceMultiSell: Loading announceMultiSell.xml...");

            try 
            
             (FileInputStream fis = new FileInputStream(file);
                InputStreamReader isr = new InputStreamReader(fis, "UTF-8"))
           {
               InputSource in = new InputSource(isr);
               in.setEncoding("UTF-8");
               Document doc = factory.newDocumentBuilder().parse(in);

               int count = 0;

               for (Node n = doc.getFirstChild(); n != null; n = n.getNextSibling())
               {
                   if (n.getNodeName().equalsIgnoreCase("list"))
                   {
                       for (Node d = n.getFirstChild(); d != null; d = d.getNextSibling())
                       {
                           if (d.getNodeName().equalsIgnoreCase("multisell"))
                           {
                               HashMap<String, Object> map = new HashMap<>();

                               Integer id = d.getAttributes().getNamedItem("id").getNodeValue().hashCode();
                               Boolean firework = Boolean.valueOf(d.getAttributes().getNamedItem("firework").getNodeValue());
                               String msg = String.valueOf(d.getAttributes().getNamedItem("msg").getNodeValue());
                               Integer typemsg = Integer.valueOf(d.getAttributes().getNamedItem("typemsg").getNodeValue());

                               map.put("firework", firework);
                               map.put("msg", msg);
                               map.put("typemsg", typemsg);

                               getList.put(id, map);
                               count++;

                               _log.info("announceMultiSell: Loaded multisell ID=" + id + " | msg=" + msg + " | firework=" + firework);
                           }
                       }
                   }
               }

               _log.info("announceMultiSell: Loaded " + count + " multisell entries.");
           }
           catch (Exception e)
           {
               _log.warning("announceMultiSell: Error " + e);
               e.printStackTrace();
           }
           
        }
		return _instance;
    }

    public boolean Contatins(int idmultisell)
    {
        return getList.containsKey(idmultisell);
    }

    private static HashMap<String,Object> getMap(int idmultisell)
    {
        return getList.get(idmultisell);
    }

    public boolean getFirework(int idmultisell)
    {
        return (Boolean) getMap(idmultisell).get("firework");
    }

    public String getMsg(int idmultisell)
    {
        return (String) getMap(idmultisell).get("msg");
    }

    public Integer getTypeMsg(int idmultisell)
    {
        return (Integer) getMap(idmultisell).get("typemsg");
    }
}
