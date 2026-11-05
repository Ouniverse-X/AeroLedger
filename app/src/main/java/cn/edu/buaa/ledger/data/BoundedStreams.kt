package cn.edu.buaa.ledger.data

import java.io.ByteArrayOutputStream
import java.io.InputStream

fun InputStream.readBytesLimited(limit:Int):ByteArray {
    val output=ByteArrayOutputStream();val buffer=ByteArray(8192)
    while(true){val size=read(buffer);if(size<0)break;require(output.size().toLong()+size<=limit){"文件超过 ${limit/1_000_000} MB"};output.write(buffer,0,size)}
    return output.toByteArray()
}
