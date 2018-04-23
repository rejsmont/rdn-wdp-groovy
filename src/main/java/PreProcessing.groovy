import ij.ImagePlus
import groovy.io.FileType
import io.scif.services.FormatService
import net.imagej.Dataset
import net.imagej.DatasetService
import net.imagej.axis.Axes
import net.imagej.ops.OpService
import net.imglib2.RandomAccessibleInterval
import net.imglib2.interpolation.randomaccess.FloorInterpolatorFactory
import net.imglib2.type.numeric.RealType
import net.imglib2.view.Views
import org.apache.commons.lang3.RandomStringUtils
import org.scijava.command.Command
import org.scijava.convert.ConvertService
import org.scijava.io.IOService
import org.yaml.snakeyaml.Yaml
import sc.fiji.hdf5.HDF5ImageJ
import org.yaml.snakeyaml.DumperOptions
import net.imglib2.FinalInterval
import java.util.concurrent.Callable

import org.scijava.plugin.Parameter
import org.scijava.plugin.Plugin

import java.util.concurrent.ExecutorCompletionService
import java.util.concurrent.Executors


@Plugin(type=Command.class)
class PreProcessing implements Command {

    @Parameter
    private IOService ioService

    @Parameter
    private ConvertService convertService

    @Parameter
    private OpService opService

    @Parameter
    private DatasetService datasetService

    @Parameter
    private FormatService formatService


    void run() {
        /**
        List<File> list = []
        def dir = new File("/Users/radoslaw.ejsmont/Desktop/171106")
        dir.eachFileRecurse (FileType.FILES) { file ->
            if (file.path.endsWith(".oif")) {
                list << file
            }
        }

        List<FileNameSet> samples = []
        list.each {
            if (it.path.toLowerCase().contains("dapi")) {
                def sample = new MATLFileNameSet(it, list, dir.path)
                if (sample.initialized) {
                    samples.add(sample)
                }
            }
        }
        **/

        List<File> list = []
        def dir = new File("/Users/radoslaw.ejsmont/Desktop/171106")
        dir.eachFileRecurse (FileType.FILES) { file ->
            if (file.path.endsWith(".h5")) {
                list << file
            }
        }

        List<FileNameSet> samples = []
        list.each {
            def sample = new HDF5FileNameSet(it)
            if (sample.initialized) {
                samples.add(sample)
            }
        }

        double[] offsets = [0.0, -1.0, -1.0]

        /**
        //def pool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() - 4)
        def pool = Executors.newFixedThreadPool(2)
        def ecs = new ExecutorCompletionService(pool)

        samples.each {
            ecs.submit(new ImagePreprocessor(it, offsets))
        }

        def submitted = samples.size()
        while (submitted > 0) {
            ecs.take().get()
            submitted--
        }

        pool.shutdown()
        **/

        samples.each {
            def processor = new ImagePreprocessor(it, offsets)
            processor.call()
        }
    }

    class DatasetFile extends File {

        private String dataset

        DatasetFile(String pathname, String dataset) {
            super(pathname)
            this.dataset = dataset
        }

        DatasetFile(String parent, String child, String dataset) {
            super(parent, child)
            this.dataset = dataset
        }

        DatasetFile(File parent, String child, String dataset) {
            super(parent, child)
            this.dataset = dataset
        }

        DatasetFile(URI uri, String dataset) {
            super(uri)
            this.dataset = dataset
        }

        def getDataset() {
            return dataset
        }
    }


    class FileNameSet {
        protected Map<String, File> sources
        protected File hdf5
        protected File yml
        protected boolean initialized

        FileNameSet() {
            sources = [:]
            hdf5 = null
            yml = null
            initialized = false
        }
    }


    class MATLFileNameSet extends FileNameSet {

        MATLFileNameSet(File dapi, List<File> fileList, String baseDir) {
            super()
            findFiles(dapi, fileList, baseDir)
        }

        MATLFileNameSet(String sample, String disc, List<File> fileList, String baseDir) {
            super()
            def regex = "^.*?$sample\\s*[-_]?\\s*?[Dd]is[ck]\\s*[-_]?\\s*$disc\\s*[-_]?\\s*[Dd][Aa][Pp][Ii].*\$"
            def dapi = fileList.find { it.path ==~ regex }
            if (dapi) {
                findFiles(dapi, fileList, baseDir)
            }
        }

        private findFiles(File dapi, List<File> fileList, String baseDir) {
            def venus = new File(dapi.path.replace("DAPI", "Venus"))
            def mcherry = new File(dapi.path.replace("DAPI", "mCherry"))
            def regex = /^.*?(\d+)\s*[-_]?\s*?[Dd]is[ck]\s*[-_]?\s*(\d+).*$/
            def match = dapi.path =~ regex
            if (match.find()) {
                def sample = match.group(1)
                def disc = match.group(2)
                if ( ! fileList.find { it.path == venus.path }) {
                    regex = "^.*?$sample\\s*[-_]?\\s*?[Dd]is[ck]\\s*[-_]?\\s*$disc\\s*[-_]?\\s*[Vv][Ee][Nn][Uu][Ss].*\$"
                    venus = fileList.find { it.path ==~ regex }
                }
                if ( ! fileList.find { it.path == mcherry.path }) {
                    regex = "^.*?$sample\\s*[-_]?\\s*?[Dd]is[ck]\\s*[-_]?\\s*$disc\\s*[-_]?\\s*[Mm][Cc][Hh][Ee][Rr][Rr]?[Yy].*\$"
                    mcherry = fileList.find { it.path ==~ regex }
                }
                if (venus && mcherry) {
                    def random_id = id_generator()
                    def hdf5BaseName = "${sample}_disc_${disc}_${random_id}.h5"
                    def ymlBaseName = "${sample}_disc_${disc}_${random_id}.yml"
                    hdf5 = new File(baseDir, hdf5BaseName)
                    yml = new File(baseDir, ymlBaseName)
                    sources.put("dapi", dapi)
                    sources.put("venus", venus)
                    sources.put("mcherry", mcherry)
                    initialized = true
                }
            }
            if (! initialized) {
                println "Failed to find data for $dapi"
            }
        }

        private id_generator(int length, String charset) {
            return RandomStringUtils.random(length, charset.toCharArray())
        }

        private id_generator(int length) {
            return RandomStringUtils.random(length, true, true)
        }

        private id_generator() {
            return RandomStringUtils.random(6, true, true)
        }
    }


    class HDF5FileNameSet extends FileNameSet {

        HDF5FileNameSet(File hdf5file) {
            super()
            /**
            sources.put("dapi", new DatasetFile(hdf5file.path, "/raw/dapi/channel0"))
            sources.put("venus", new DatasetFile(hdf5file.path, "/raw/venus/channel0"))
            sources.put("mcherry", new DatasetFile(hdf5file.path, "/raw/mcherry/channel0"))
             **/
            sources.put("dapi", new DatasetFile(hdf5file.path, "/raw/dapi"))
            sources.put("venus", new DatasetFile(hdf5file.path, "/raw/venus"))
            sources.put("mcherry", new DatasetFile(hdf5file.path, "/raw/mcherry"))
            hdf5 = hdf5file
            initialized = true
        }
    }


    class SourceImageSet {

        private Map<String, Dataset> images
        private boolean initialized

        SourceImageSet(FileNameSet names) {
            images = [:]
            initialized = openImages(names)
        }

        private openImages(FileNameSet names) {
            names.sources.each { k, v ->
                Dataset image
                if (v instanceof DatasetFile) {
                    def imp = HDF5ImageJ.hdf5read(v.path, v.dataset, 'zyx')
                    image = convertService.convert(imp, Dataset.class).duplicate()
                    imp.close()
                } else {
                    image = ioService.open(v.path) as Dataset
                }

                if (! checkImage(image)) {
                    return false
                }
                images.put(k, image)
            }
            return true
        }

        private checkImage(Dataset image) {
            if (image) {
                int d = image.dimensionIndex(Axes.Z)
                if (d >= 0 && image.max(d) > 20) {
                    return true
                }
            }
            return false
        }

        def getReference() {
            return images.get("dapi")
        }
    }


    class MetadataSet {
        private Map<String, Object> metadata
        private boolean initialized

        MetadataSet(FileNameSet names) {
            metadata = [:]
            initialized = true
            names.sources.each { k, v ->
                def meta = readMetadata(v)
                if (meta) {
                    metadata.put(k, meta)
                } else {
                    initialized = false
                }
            }
        }

        def readMetadata(File image) {
            if (! image) {
                return null
            }
            def meta = [:]
            def format = formatService.getFormat(image.path)
            def metadata = format.createParser().parse(image)
            metadata.getTable().entrySet().each { entry ->
                def key = entry.key
                def value = entry.value
                def match = key =~ /^\[(.*?)\]\s*(.*)$/
                if (match.find()) {
                    def group = match.group(1)
                    key = match.group(2)
                    if (! meta.containsKey(group)) {
                        meta[group] = [:]
                    }
                    meta[group][key] = value
                } else {
                    meta[key] = value
                }
            }
            return meta
        }

        def getYaml() {
            def options = new DumperOptions()
            options.setDefaultFlowStyle(DumperOptions.FlowStyle.FLOW)
            options.setPrettyFlow(true)
            options.setIndent(4)
            def yaml = new Yaml(options)
            return yaml.dump(metadata)
        }
    }

    class ImageFusion {

        private List<RandomAccessibleInterval> images
        private Dataset reference
        private boolean initialized

        ImageFusion(SourceImageSet sourceSet) {
            if (sourceSet.initialized) {
                def sources = sourceSet.images
                reference = sourceSet.reference
                images = []
                sources.each { k, image ->
                    def d = image.dimensionIndex(Axes.CHANNEL)
                    def scaled = scaleImage(image)
                    if (d >= 0) {
                        for (def c = scaled.min(d); c <= scaled.max(d); c++) {
                            def channel = Views.hyperSlice(scaled, d, c)
                            images.add(channel)
                        }
                    } else {
                        images.add(scaled)
                    }
                }
                initialized = true
            } else {
                initialized = false
            }
        }

        private scaleImage(Dataset image) {
            def scales = []
            def resize = false
            for (def d = 0; d < image.numDimensions(); d++) {
                def axis = image.axis(d)
                if (axis.type().isSpatial()) {
                    def bd = reference.dimensionIndex(axis.type())
                    def scale = (reference.max(bd) - reference.min(bd) + 1.0) / (image.max(d) - image.min(d) + 1.0)
                    resize = resize || (scale != 1.0)
                    scales.add(scale)
                } else {
                    scales.add(1.0)
                }
            }
            return resize ? opService.transform().scaleView(
                    image as RandomAccessibleInterval, scales as double[], new FloorInterpolatorFactory()) : image
        }

        def getAlignedImage(double[] offsets) {
            if (! initialized) {
                return null
            }
            def zidx = reference.dimensionIndex(Axes.Z)
            def zaxis = reference.axis(zidx)
            def zshifts = []

            if (offsets.length == images.size()) {
                zshifts = offsets.collect { Math.round(zaxis.rawValue(it)) }
            } else {
                zshifts = (0..images.size()).collect { 0 }
            }

            def zsmin = zshifts.min() < 0 ? zshifts.min() : 0
            def zsmax = zshifts.max() > 0 ? zshifts.max() : 0
            def zslices = reference.max(zidx) + zsmax - zsmin

            def stack = []
            def zero = reference.firstElement().createVariable() as RealType
            zero.setZero()
            images.eachWithIndex { image, i ->
                image = Views.extendValue(image, zero)
                def imin = []
                def imax = []

                for (def d = 0; d < image.numDimensions(); d++) {
                    def dmin, dmax
                    if (d == (reference.dimensionIndex(Axes.Z) - 1)) {
                        dmin = reference.min(d) + (zshifts[i] as long) - zsmax
                        dmax = dmin + zslices
                    } else {
                        dmin = reference.min(d)
                        dmax = reference.max(d)
                    }
                    imin.add(dmin)
                    imax.add(dmax)
                }
                stack.add(Views.offsetInterval(image, new FinalInterval(imin as long[], imax as long[])))
            }
            return createResult(Views.stack(stack))
        }

        def getImage() {
            return initialized ? createResult(Views.stack(images)) : null
        }

        private createResult(RandomAccessibleInterval image) {
            def result = datasetService.create(image)
            result.setAxis(reference.axis(reference.dimensionIndex(Axes.X)), 0)
            result.setAxis(reference.axis(reference.dimensionIndex(Axes.Y)), 1)
            result.setAxis(reference.axis(reference.dimensionIndex(Axes.Z)), 2)
            result.axis(3).setType(Axes.CHANNEL)
            return result

        }
    }

    class ImagePreprocessor implements Callable {

        private double[] offsets
        private FileNameSet files
        private boolean initialized
        private SourceImageSet sources
        private MetadataSet metadata
        private ImageFusion processed
        private boolean called

        ImagePreprocessor(FileNameSet files, double[] offsets) {
            this.offsets = offsets
            this.files = files
            initialized = false
            sources = null
            metadata = null
            processed = null
            called = false
        }

        def initialize() {
            if (files) {
                sources = new SourceImageSet(files)
                if (files.yml) {
                    metadata = new MetadataSet(files)
                }
                if (sources) {
                    processed = new ImageFusion(sources)
                    initialized = true
                }
            }
        }

        def saveMetadata() {
            if ((! initialized) || (! files.yml)) {
                return
            }
            println "Exporting metadata $files.yml."
            files.yml.write(metadata.yaml)
            println "Metadata export done."
        }

        def saveRaw() {
            if ((! initialized) || (! sources.initialized)) {
                return
            }
            println "Exporting raw data $files.hdf5."
            sources.images.each { name, image ->
                def imp = convertService.convert(image, ImagePlus)
                if (image.dimension(Axes.CHANNEL) > 1) {
                    HDF5ImageJ.hdf5write(imp, files.hdf5.path, "/raw2/$name/channel{c}", "", "%d", 9, false)
                } else {
                    HDF5ImageJ.hdf5write(imp, files.hdf5.path, "/raw2/$name", "", "", 9, false)
                }
                imp.close()
            }
            println "Raw data export done."
        }

        def saveAligned() {
            if ((! initialized) || (! processed.initialized)) {
                return
            }
            println "Exporting aligned data $files.hdf5."
            def image = processed.getAlignedImage(offsets)
            if (image) {
                def imp = convertService.convert(image, ImagePlus)
                HDF5ImageJ.hdf5write(imp, files.hdf5.path, "/aligned2/channel{c}", "", "%d", 0, false)
                imp.close()
            }
            println "Aligned data export done."
        }

        Object call() throws Exception {
            println "Initializing..."
            initialize()
            println "Saving metadata..."
            saveMetadata()
            println "Saving raw images..."
            saveRaw()
            println "Saving aligned images..."
            saveAligned()
            println "Done!"
            called = true
            return this
        }
    }
}
