import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * This class provides all code necessary to take a query box and produce
 * a query result. The getMapRaster method must return a Map containing all
 * seven of the required fields, otherwise the front end code will probably
 * not draw the output correctly.
 */
public class Rasterer
{
    private QuadTree quadTree;

    private class QuadTree
    {
        private int totalDepth;
        private String imgRoot;
        private static final double IMG_LON_PIXEL = 256;
        private static final double ROOT_LRLON = -122.2119140625;
        private static final double ROOT_ULLON = -122.2998046875;
        private static final double ROOT_LRLAT = 37.82280243352756;
        private static final double ROOT_ULLAT = 37.892195547244356;

        private QuadTree(String imgRoot)
        {
            this.imgRoot = imgRoot;
            totalDepth = checkImgRoot(imgRoot);
        }

        private String number2FileName(int n)
        {
            if (n < 0)
                throw new IllegalArgumentException();
            if (n == 0)
                return "root";
            StringBuilder fileName = new StringBuilder();
            int x;
            while (n > 4)
            {
                x = n % 4;
                n /= 4;
                if (x == 0)
                {
                    x = 4;
                    n -= 1;
                }
                fileName.append(x);
            }
            fileName.append(n);
            fileName = fileName.reverse();
            return fileName.toString();
        }

        private Map<String, Double> fileName2ImgProperty(String fileName)
        {
            char[] name = fileName.toCharArray();
            double lrlon = ROOT_LRLON;
            double ullon = ROOT_ULLON;
            double lrlat = ROOT_LRLAT;
            double ullat = ROOT_ULLAT;
            if (!fileName.equals("root"))
            {
                for (char x: name)
                {
                    if (x == '1')
                    {
                        lrlon = (ullon + lrlon) / 2;
                        lrlat = (ullat + lrlat) / 2;
                    }
                    else if (x == '2')
                    {
                        ullon = (ullon + lrlon) / 2;
                        lrlat = (ullat + lrlat) / 2;
                    }
                    else if (x == '3')
                    {
                        ullat = (ullat + lrlat) / 2;
                        lrlon = (ullon + lrlon) / 2;
                    }
                    else if (x == '4')
                    {
                        ullon = (ullon + lrlon) / 2;
                        ullat = (ullat + lrlat) / 2;
                    }
                    else
                        throw new IllegalArgumentException();
                }
            }
            Map<String, Double> imgProperty = new HashMap<>();
            imgProperty.put("lrlon", lrlon);
            imgProperty.put("lrlat", lrlat);
            imgProperty.put("ullon", ullon);
            imgProperty.put("ullat", ullat);
            imgProperty.put("widthPixel", IMG_LON_PIXEL);
            return imgProperty;
        }

        private int checkImgRoot(String imgRoot)
        {
            File file = new File(imgRoot);
            if (!file.isDirectory())
                return -1;
            String[] fileNames = file.list();
            if (fileNames == null)
                return -1;
            int imgNumber = fileNames.length;
            double zoomLevel = (Math.log(imgNumber * 3 + 1) / Math.log(4)) - 1;
            if (zoomLevel != (int) zoomLevel)
                return -1;
            else
                return (int) zoomLevel;
        }

        private boolean imgCover(Map<String, Double> img, Map<String, Double> query)
        {
            return !(img.get("lrlon") <= query.get("ullon") ||
                     img.get("ullon") >= query.get("lrlon") ||
                     img.get("lrlat") >= query.get("ullat") ||
                     img.get("ullat") <= query.get("lrlat"));
        }

        private String moveRight(String imgName)
        {
            char[] name = imgName.toCharArray();
            int index = name.length - 1;
            while (index >= 0)
            {
                if (name[index] == '1')
                {
                    name[index] = '2';
                    break;
                }
                else if (name[index] == '3')
                {
                    name[index] = '4';
                    break;
                }
                else if (name[index] == '2')
                {
                    name[index] = '1';
                    index -= 1;
                }
                else if (name[index] == '4')
                {
                    name[index] = '3';
                    index -= 1;
                }
                else
                    throw new IllegalArgumentException();
            }
            return String.valueOf(name);
        }

        private String moveDown(String imgName)
        {
            char[] name = imgName.toCharArray();
            int index = name.length - 1;
            while (index >= 0)
            {
                if (name[index] == '1')
                {
                    name[index] = '3';
                    break;
                }
                else if (name[index] == '2')
                {
                    name[index] = '4';
                    break;
                }
                else if (name[index] == '3')
                {
                    name[index] = '1';
                    index -= 1;
                }
                else if (name[index] == '4')
                {
                    name[index] = '2';
                    index -= 1;
                }
                else
                    throw new IllegalArgumentException();
            }
            return String.valueOf(name);
        }

        private void fillImgs(String[][] imgs, String ulImgName, String lrImgName)
        {
            int i = 0, j = 0;
            double endLon = fileName2ImgProperty(lrImgName).get("ullon");
            String currentImgName = ulImgName;
            String lineStartImgName = ulImgName;
            while (true)
            {
                imgs[i][j] = imgRoot + currentImgName + ".png";
                if (currentImgName.equals(lrImgName))
                    break;
                if (fileName2ImgProperty(currentImgName).get("ullon") == endLon)
                {
                    i += 1;
                    j = 0;
                    lineStartImgName = moveDown(lineStartImgName);
                    currentImgName = lineStartImgName;
                }
                else
                {
                    j += 1;
                    currentImgName = moveRight(currentImgName);
                }
            }
        }

        public Map<String, Object> getImgs(Map<String, Double> params)
        {
            Map<String, Object> raster = new HashMap<>();
            double queryLonDPP = Math.abs((params.get("ullon") - params.get("lrlon")) / params.get("w"));
            Integer[] imgULLR = new Integer[]{-1, -1};
            getImgs(params, imgULLR, 0, queryLonDPP);
            if (imgULLR[0] == -1)
                raster.put("query_success", false);
            else
            {
                raster.put("query_success", true);
                String ulImgName = number2FileName(imgULLR[0]);
                String lrImgName = number2FileName(imgULLR[1]);
                Map<String, Double> ulImgProperty = fileName2ImgProperty(ulImgName);
                Map<String, Double> lrImgProperty = fileName2ImgProperty(lrImgName);
                if (ulImgName.equals("root"))
                    raster.put("depth", 0);
                else
                    raster.put("depth", ulImgName.length());
                raster.put("raster_ul_lon", ulImgProperty.get("ullon"));
                raster.put("raster_ul_lat", ulImgProperty.get("ullat"));
                raster.put("raster_lr_lon", lrImgProperty.get("lrlon"));
                raster.put("raster_lr_lat", lrImgProperty.get("lrlat"));
                int n = 1, m = 1;
                String checkImg = ulImgName;
                while (!fileName2ImgProperty(checkImg).get("ullon").equals(lrImgProperty.get("ullon")))
                {
                    n += 1;
                    checkImg = moveRight(checkImg);
                }
                while (!fileName2ImgProperty(checkImg).get("ullat").equals(lrImgProperty.get("ullat")))
                {
                    m += 1;
                    checkImg = moveDown(checkImg);
                }
                String[][] rasterImgs = new String[m][n];
                fillImgs(rasterImgs, ulImgName, lrImgName);
                raster.put("render_grid", rasterImgs);
            }
            return raster;
        }

        private void getImgs(Map<String, Double> params, Integer[] imgULLR, int x, double queryLonDPP)
        {
            String fileName = number2FileName(x);
            Map<String, Double> imgProperty = fileName2ImgProperty(fileName);
            int depth = fileName.length();
            double imgLonDPP = Math.abs((imgProperty.get("ullon") - imgProperty.get("lrlon")) / imgProperty.get("widthPixel"));
            if (imgCover(imgProperty, params))
            {
                if (depth >= totalDepth || imgLonDPP <= queryLonDPP )
                {
                    if (imgULLR[0] == -1)
                    {
                        imgULLR[0] = x;
                    }
                    imgULLR[1] = x;
                }
                else
                {
                    getImgs(params, imgULLR, (x + 1) * 4 - 3, queryLonDPP);
                    getImgs(params, imgULLR, (x + 1) * 4 - 2, queryLonDPP);
                    getImgs(params, imgULLR, (x + 1) * 4 - 1, queryLonDPP);
                    getImgs(params, imgULLR, (x + 1) * 4, queryLonDPP);
                }
            }
        }
    }

    /** imgRoot is the name of the directory containing the images.
     *  You may not actually need this for your class. */
    public Rasterer(String imgRoot)
    {
        quadTree = new QuadTree(imgRoot);
    }

    /**
     * Takes a user query and finds the grid of images that best matches the query. These
     * images will be combined into one big image (rastered) by the front end. <br>
     * <p>
     *     The grid of images must obey the following properties, where image in the
     *     grid is referred to as a "tile".
     *     <ul>
     *         <li>Has dimensions of at least w by h, where w and h are the user viewport width
     *         and height.</li>
     *         <li>The tiles collected must cover the most longitudinal distance per pixel
     *         (LonDPP) possible, while still covering less than or equal to the amount of
     *         longitudinal distance per pixel in the query box for the user viewport size. </li>
     *         <li>Contains all tiles that intersect the query bounding box that fulfill the
     *         above condition.</li>
     *         <li>The tiles must be arranged in-order to reconstruct the full image.</li>
     *     </ul>
     * </p>
     * @param params Map of the HTTP GET request's query parameters - the query box and
     *               the user viewport width and height.
     *
     * @return A map of results for the front end as specified:
     * "render_grid"   -> String[][], the files to display
     * "raster_ul_lon" -> Number, the bounding upper left longitude of the rastered image <br>
     * "raster_ul_lat" -> Number, the bounding upper left latitude of the rastered image <br>
     * "raster_lr_lon" -> Number, the bounding lower right longitude of the rastered image <br>
     * "raster_lr_lat" -> Number, the bounding lower right latitude of the rastered image <br>
     * "depth"         -> Number, the 1-indexed quadtree depth of the nodes of the rastered image.
     *                    Can also be interpreted as the length of the numbers in the image
     *                    string. <br>
     * "query_success" -> Boolean, whether the query was able to successfully complete. Don't
     *                    forget to set this to true! <br>
     */
    public Map<String, Object> getMapRaster(Map<String, Double> params) {
        System.out.println(params);
        return quadTree.getImgs(params);
    }

}
