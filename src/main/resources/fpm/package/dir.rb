#   Copyright 2013, LOYAL3 Labs
#
#   Licensed under the Apache License, Version 2.0 (the "License");
#   you may not use this file except in compliance with the License.
#   You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
#   Unless required by applicable law or agreed to in writing, software
#   distributed under the License is distributed on an "AS IS" BASIS,
#   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#   See the License for the specific language governing permissions and
#   limitations under the License.

require 'rubygems'
require 'fpm'

class FPM::Package::Dir < FPM::Package

  # Copy a path.
  #
  # Files will be hardlinked if possible, but copied otherwise.
  # Symlinks should be copied as symlinks.
  def copy(source, destination)
    directory = File.dirname(destination)
    if !File.directory?(directory)
      FileUtils.mkdir_p(directory)
    end

    if File.directory?(source)
      if !File.symlink?(source)
        # Create a directory if this path is a directory
        @logger.debug("Creating", :directory => destination)
        if !File.directory?(destination)
          FileUtils.mkdir(destination)
        end
      else
        # Linking symlinked directories causes a hardlink to be created, which
        # results in the source directory being wiped out during cleanup,
        # so copy the symlink.
        @logger.debug("Copying symlinked directory", :source => source,
                      :destination => destination)
        FileUtils.copy_entry(source, destination)
      end
    else
      # Otherwise try copying the file.
      begin
        @logger.debug("Linking", :source => source, :destination => destination)
        File.link(source, destination)
      rescue Errno::EXDEV, Errno::EPERM, Errno::EEXIST, Errno::ENOENT
        # Hardlink attempt failed, copy it instead
        @logger.debug("Copying", :source => source, :destination => destination)
        FileUtils.copy_entry(source, destination)
      end
    end

    copy_metadata(source, destination)
  end # def copy
end
