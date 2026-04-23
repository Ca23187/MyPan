export default {
  all: {
    accept: "*",
    fileCategory: null,      // all 不传 fileCategory
    defaultScope: 0,         // 当前层
  },
  video: {
    accept: ".mp4,.avi,.rmvb,.mkv,.mov",
    fileCategory: 1,         // 后端 FileInfoQuery.fileCategory
    defaultScope: 2,         // 全盘
  },
  music: {
    accept: ".mp3,.wav,.wma,.mp2,.flac,.midi,.ra,.ape,.aac,.cda",
    fileCategory: 2,
    defaultScope: 2,
  },
  image: {
    accept: ".jpeg,.jpg,.png,.gif,.bmp,.dds,.psd,.pdt,.webp,.xmp,.svg,.tiff",
    fileCategory: 3,
    defaultScope: 2,
  },
  doc: {
    accept: ".pdf,.doc,.docx,.xls,.xlsx,.txt,.csv",
    fileCategory: 4,
    defaultScope: 2,
  },
  others: {
    accept: "*",
    fileCategory: 5,
    defaultScope: 2,
  },
  search: { 
    accept: "*", 
    fileCategory: null 
  },
};
