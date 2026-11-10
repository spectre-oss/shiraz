// SPDX-License-Identifier: GPL-3.0-only
// Export bounded packets from a generated AAC fixture; no host media is read.
#include <AudioToolbox/AudioToolbox.h>
#include <cstdio>
#include <cstring>
#include <vector>
static bool word(FILE *file,uint32_t value){unsigned char bytes[]={static_cast<unsigned char>(value),static_cast<unsigned char>(value>>8),static_cast<unsigned char>(value>>16),static_cast<unsigned char>(value>>24)};return std::fwrite(bytes,1,4,file)==4;}
int main(int argc,char **argv){
 if(argc!=3)return 2;auto url=CFURLCreateFromFileSystemRepresentation(nullptr,reinterpret_cast<const UInt8 *>(argv[1]),std::strlen(argv[1]),false);AudioFileID file=nullptr;auto status=AudioFileOpenURL(url,kAudioFileReadPermission,0,&file);CFRelease(url);if(status)return 3;
 UInt64 packets=0;UInt32 size=sizeof(packets),maximum=0;status=AudioFileGetProperty(file,kAudioFilePropertyAudioDataPacketCount,&size,&packets);size=sizeof(maximum);if(status||AudioFileGetProperty(file,kAudioFilePropertyMaximumPacketSize,&size,&maximum)||packets>256||!packets||maximum>65536||!maximum){AudioFileClose(file);return 4;}
 FILE *output=std::fopen(argv[2],"wb");if(!output){AudioFileClose(file);return 5;}bool ok=std::fwrite("AAC1",1,4,output)==4&&word(output,uint32_t(packets));std::vector<unsigned char> bytes(maximum);
 for(UInt64 i=0;ok&&i<packets;i++){UInt32 count=1,length=maximum;AudioStreamPacketDescription description{};status=AudioFileReadPacketData(file,false,&length,&description,SInt64(i),&count,bytes.data());if(status||count!=1||!length||length>maximum){ok=false;break;}ok=word(output,length)&&std::fwrite(bytes.data(),1,length,output)==length;}
 std::fclose(output);AudioFileClose(file);return ok?0:6;
}
